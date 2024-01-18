package player8;

import battlecode.common.*;
import player8.fast.FastIntLocMap;
import player8.Communicator;

import java.util.Random;

public strictfp class RobotPlayer {
    // IF YOU HAVE ANY ARBITRARILY CHOSEN CONSTANTS FOR THE BOT, PUT THEM HERE AS FINAL VARIABLES HERE
    // SO THAT WE CAN MORE EASILY TUNE THEM.

    // END ARBITRARY CONSTANTS

    static int turnCount = 0;
    static Random rng;
    static MapLocation[] sharedAllyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedEnemyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedAllyFlagCarrierInfo = new MapLocation[3];

    static char[][] mapRecord;

    static MapLocation bottomLeft = new MapLocation(0,0);
    static MapLocation bottomRight, topRight, topLeft;



    static boolean[] symmetries= {true, true, true}; //rot, hor, vert

    static MapLocation spawnPos;

    static boolean isDefender = false;

    static GlobalUpgrade[] globalUpgrades = {GlobalUpgrade.ACTION, GlobalUpgrade.HEALING};

    static FastIntLocMap lastSeenPrevEnemyLoc = new FastIntLocMap(); //from previous turn only!!

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            try {

                if (!rc.isSpawned()) {//spawn
                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    MapLocation mySpawnLoc = spawnLocs[rc.getID() % spawnLocs.length];
                    if (rc.canSpawn(mySpawnLoc)) {
                        rc.spawn(mySpawnLoc);
                        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo);
                        indicateSharedInfo(rc);
                        Communicator.updateEnemyFlagLocations(rc);
                        rng = new Random(rc.getID());
                        if (rc.getRoundNum() == 1 && rc.readSharedArray(63) == 0) {
                            Communicator.wipeArray(rc, (1 << 16) - 1);
                        }
                        spawnPos = rc.getLocation();
                        mapRecord = new char[rc.getMapWidth()][rc.getMapHeight()];
                        topLeft = new MapLocation(0, rc.getMapHeight() - 1);
                        topRight = new MapLocation(rc.getMapWidth() - 1, rc.getMapHeight() - 1);
                        bottomRight = new MapLocation(rc.getMapWidth() - 1, 0);

                        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1, rc.getTeam());
                        for (FlagInfo flag : nearbyFlags) {
                            if (!flag.isPickedUp() && rc.canPickupFlag(flag.getLocation())) {
                                boolean isUntouched = true;
                                for (int i = 0; i < 3; i ++) {
                                    if (sharedAllyFlagInfo[i] != null && sharedAllyFlagInfo[i].equals(flag.getLocation())) {
                                        isUntouched = false;
                                        break;
                                    }
                                }
                                if (isUntouched) {
                                    isDefender = true;
                                    rc.pickupFlag(flag.getLocation());
                                    newFlagLoc = rc.getLocation();
                                }
                            }
                        }
                    } else {
                        Clock.yield();
                        continue;
                    }
                } else {
                    Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo);
                    indicateSharedInfo(rc);
                    Communicator.updateEnemyFlagLocations(rc);
                }
                boolean isFirst=false;

                if (rc.getRoundNum() != Communicator.interpretNumber(rc, Communicator.roundNumStart,11)) {
                    Communicator.writeNumber(rc, Communicator.roundNumStart, rc.getRoundNum(),11);
                    isFirst=true;
                }

                if (isFirst) {
                    for (int i = 0; i < 3; i ++) {
                        if (!Communicator.readBit(rc, Communicator.isAllyFlagCarrierAliveStart+i)) {
                            Communicator.writeLocation(rc, Communicator.allyFlagCarriersStart+i*12, new MapLocation(63,63));
                        }
                    }
                    Communicator.writeNumber(rc, Communicator.isAllyFlagCarrierAliveStart, 0, 3);
                }
                if (!rc.hasFlag() && flagInd != -1 && rc.getRoundNum()-lastFlagRound <= 1) {
                    Communicator.writeBit(rc, Communicator.enemyFlagCapturedStart+flagInd,true);
                    flagInd = -1;
                    lastFlagRound=-1;
                }

                MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos();

                if (rc.canBuyGlobal(globalUpgrades[0])) {
                    rc.buyGlobal(globalUpgrades[0]);
                }
                if (rc.canBuyGlobal(globalUpgrades[1])) {
                    rc.buyGlobal(globalUpgrades[1]);
                }
                if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS - 35) {
                    if (isDefender) {
                        defend(rc);
                    } else {
                        setup(rc);
                    }
                } else if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS) {//TODO: move this into setup
                    MapLocation target = targetEnemySpawn(rc);
                    Navigator.moveToward(rc, target);
                } else {
                    attack(rc);
                }

                RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent()); //TODO: remove redundancy
                for (RobotInfo nearbyEnemy : nearbyEnemies) {
                    lastSeenPrevEnemyLoc.addReplace(nearbyEnemy.getID(), nearbyEnemy.getLocation());
                }
//                rc.setIndicatorString(String.valueOf(Communicator.interpretNumber(rc, Communicator.isAllyFlagCarrierAliveStart,3)));
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    public static boolean onBorder(RobotController rc) {
        return rc.getLocation().x ==0  || rc.getLocation().x==rc.getMapWidth()-1 ||rc.getLocation().y ==0  || rc.getLocation().y==rc.getMapHeight()-1;
    }

    public static void indicateSharedInfo(RobotController rc) {
        for (MapLocation loc : sharedAllyFlagInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 0, 100, 0);
            }
        }
        for (MapLocation loc : sharedEnemyFlagInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 100,0, 0);
            }
        }
        for (MapLocation loc : sharedAllyFlagCarrierInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 0, 0,255);
            }
        }
    }

    static MapLocation newFlagLoc;


    static int flagRole = 0;

    public static int distanceCombined(RobotController rc, MapLocation loc, MapLocation[] locs) {
        int result = 0;
        for (MapLocation currLoc : locs) {
            result += loc.distanceSquaredTo(currLoc);
        }
        return result;
    }

    public static MapLocation clampLoc(RobotController rc, MapLocation loc) {
        int x = loc.x;
        int y = loc.y;
        if (x < 0) {
            x = 0;
        }
        if (x>=rc.getMapWidth()) {
            x = rc.getMapWidth()-1;
        }
        if (y<0) {
            y=0;
        }
        if (y>=rc.getMapHeight()) {
            y=rc.getMapHeight()-1;
        }
        return new MapLocation(x,y);
    }

    public static void defend(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() >= GameConstants.SETUP_ROUNDS) {
            fight(rc, rc.senseNearbyRobots(-1, rc.getTeam().opponent()), rc.senseNearbyRobots(-1, rc.getTeam()), rc.senseNearbyMapInfos(), false);
        }

        MapLocation closestFlag = getClosestPlacedAllyFlag(rc);
        int minDistSqToPlacedFlag = Util.BigNum;
        if (closestFlag != null) {
            minDistSqToPlacedFlag = rc.getLocation().distanceSquaredTo(closestFlag);
        }

        if (rc.hasFlag()) {
            if (rc.canDropFlag(newFlagLoc)) {
                rc.dropFlag(newFlagLoc);
                Communicator.writeAllyFlagLocation(rc, newFlagLoc);
            } else {
                Navigator.moveToward(rc, newFlagLoc);
            }
        } else {
            if (minDistSqToPlacedFlag > 9 && closestFlag != null) {
                Navigator.moveToward(rc, closestFlag);
            } else {
                rc.setIndicatorString("wandering " + minDistSqToPlacedFlag);
                Navigator.wander(rc, rng);
            }
        }
    }

    public static MapLocation getClosestPlacedAllyFlag(RobotController rc) {
        int minDistSqToPlacedFlag = Util.BigNum;
        MapLocation closestFlag = null;
        for (int i = 0; i < 3;i ++) {
            if (sharedAllyFlagInfo[i] != null) {
                if (sharedAllyFlagInfo[i].distanceSquaredTo(rc.getLocation()) < minDistSqToPlacedFlag) {
                    minDistSqToPlacedFlag = sharedAllyFlagInfo[i].distanceSquaredTo(rc.getLocation());
                    closestFlag = sharedAllyFlagInfo[i];
                }
            }
        }
        return closestFlag;
    }

    public static void setup(RobotController rc) throws GameActionException {
        MapLocation closestFlag = getClosestPlacedAllyFlag(rc);
        int minDistSqToPlacedFlag = Util.BigNum;
        if (closestFlag != null) {
            minDistSqToPlacedFlag = rc.getLocation().distanceSquaredTo(closestFlag);
        }
        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(-1);
        MapLocation target = chooseClosestTarget(rc.getLocation(), nearbyCrumbs);
        if (target != null) {
            Navigator.moveToward(rc, target);
        } else {
            Navigator.wander(rc,rng);
        }
    }

    public static Symmetry getSymmetry() {
        if (symmetries[0]) {
            return Symmetry.ROTATIONAL;
        } else if (symmetries[1]) {
            return Symmetry.HORIZONTAL;
        } else {
            return Symmetry.VERTICAL;
        }
    }


    public static MapLocation chooseClosestTarget(MapLocation myLoc, MapLocation[] locs) {
        MapLocation target = null;
        int minDistSq = Util.BigNum;
        for (MapLocation loc: locs) {
            if (loc == null) continue;
            int currDistSq =loc.distanceSquaredTo(myLoc);
            if (currDistSq < minDistSq) {
                minDistSq = currDistSq;
                target = loc;
            }
        }
        return target;
    }

    public static MapLocation chooseClosestAttackTarget(MapLocation myLoc, RobotInfo[] nearbyEnemies) {
        MapLocation target = null;
        int minDistSq = Util.BigNum;
        for (RobotInfo enemy : nearbyEnemies) {
            MapLocation loc = enemy.getLocation();
            if (loc == null) continue;
            int currDistSq =loc.distanceSquaredTo(myLoc);
            if (currDistSq < minDistSq) {
                minDistSq = currDistSq;
                target = loc;
            }
        }
        return target;
    }

    static boolean isEscorting = false;
    static MapLocation fillLoc = null;

    public static MapLocation targetEnemySpawn(RobotController rc) {
        Symmetry currSymmetry = getSymmetry();

        MapLocation target;

        if (currSymmetry == Symmetry.ROTATIONAL) {
            target = new MapLocation(rc.getMapWidth() - spawnPos.x, rc.getMapHeight() - spawnPos.y);
        } else if (currSymmetry == Symmetry.HORIZONTAL) {

            target = new MapLocation(spawnPos.x, rc.getMapHeight() - spawnPos.y);
        } else {
            target = new MapLocation(rc.getMapWidth() - spawnPos.x, spawnPos.y);

        }
        return target;
    }

    static int flagInd = -1;
    static int lastFlagRound = -1;

    public static void attack(RobotController rc) throws GameActionException{
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos();
        MapLocation[] broadcastFlagLocations = rc.senseBroadcastFlagLocations();

        if (fillLoc != null) {
            if (rc.canFill(fillLoc)) {
                rc.fill(fillLoc);
                fillLoc = null;
            }
        }

        FlagInfo[] nearbyEnemyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());
        for (FlagInfo nearbyEnemyFlag : nearbyEnemyFlags) {
            if (rc.canPickupFlag(nearbyEnemyFlag.getLocation())) {
                rc.pickupFlag(nearbyEnemyFlag.getLocation());
            }
        }
        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(-1);
        MapLocation crumbTarget = chooseClosestTarget(rc.getLocation(), nearbyCrumbs);
        if (crumbTarget != null && nearbyAllies.length-nearbyEnemies.length >= 1) {
            Navigator.moveToward(rc, crumbTarget);
        }
        fight(rc, nearbyEnemies, nearbyAllies, nearbyMapInfos, true);

        isEscorting = false;
        for (RobotInfo ally : nearbyAllies) {
            int distSqFromAlly = ally.getLocation().distanceSquaredTo(rc.getLocation());
            if (ally.hasFlag()) {
                isEscorting = true;
                Direction direction = ally.getLocation().directionTo(chooseClosestTarget(ally.getLocation(), rc.getAllySpawnLocations()));
                int distSqFromAllyAfterAllyMoves = ally.getLocation().add(direction).distanceSquaredTo(rc.getLocation());
                if (distSqFromAllyAfterAllyMoves < distSqFromAlly && ally.getLocation().distanceSquaredTo(rc.getLocation()) < 4) {
                    Navigator.tryDir(rc, direction);
                }
                if (distSqFromAllyAfterAllyMoves >= distSqFromAlly) {
                    Navigator.tryDir(rc, direction);
                }
            }
        }

        if (rc.hasFlag()) {
            lastFlagRound=rc.getRoundNum();
            MapLocation target = chooseClosestTarget(rc.getLocation(), rc.getAllySpawnLocations());
            fillLoc = Navigator.moveToward(rc, target);
            int flagId = rc.senseNearbyFlags(0)[0].getID();
            for (int i = 0; i < 3; i ++) {
                if (flagId == Communicator.interpretNumber(rc, Communicator.enemyFlagIDsStart+14*i, 14)) {
                    flagInd = i;
                    Communicator.writeLocation(rc, Communicator.allyFlagCarriersStart+i*12, rc.getLocation());
                    Communicator.writeBit(rc, Communicator.isAllyFlagCarrierAliveStart+i, true);
                }
            }
        } else if (!isEscorting && (sharedEnemyFlagInfo[0] != null || sharedEnemyFlagInfo[1] != null || sharedEnemyFlagInfo[2] != null)) {
            MapLocation target = null;
            int minDistSq = Util.BigNum;
            String report = "";

            for (int i = 0;i<3;i++) {
                MapLocation loc = sharedEnemyFlagInfo[i];
                if (loc == null || sharedAllyFlagCarrierInfo[i] != null || Communicator.readBit(rc, Communicator.enemyFlagCapturedStart+i)) continue;
                int currDistSq =loc.distanceSquaredTo(rc.getLocation());
                if (currDistSq < minDistSq) {
                    minDistSq = currDistSq;
                    target = loc;
                }
            }
            if (target != null) {
                Navigator.moveToward(rc, target);
            }
        }
        if (rc.getMovementCooldownTurns()<10 && !isEscorting) {
            if (broadcastFlagLocations.length==0) {
                MapLocation target = targetEnemySpawn(rc);
                Navigator.moveToward(rc, target);
            } else {
                MapLocation closestBroadcastLoc = chooseClosestTarget(rc.getLocation(), broadcastFlagLocations);
                Navigator.moveToward(rc, closestBroadcastLoc);
            }
        }
    }

    public static MapLocation chooseBestAttackTarget(RobotController rc, RobotInfo[] nearbyEnemies, boolean mustBeAttackable) throws GameActionException {
        int minEnemyHealth=1001;
        MapLocation attackTarget = null;
        int minEnemyId = Util.BigNum;

        for (RobotInfo enemy : nearbyEnemies) {
            boolean canAttack = !mustBeAttackable || rc.canAttack(enemy.getLocation());
            if (enemy.hasFlag() && canAttack) {
                attackTarget = enemy.getLocation();
                break;
            }
            if (canAttack && enemy.getHealth()<minEnemyHealth) {
                minEnemyHealth = enemy.getHealth();
                attackTarget = enemy.getLocation();
                minEnemyId=enemy.getID();
            } else if (canAttack && enemy.getHealth() == minEnemyHealth) {
                if (enemy.getID() < minEnemyId) {
                    minEnemyId = enemy.getID();
                    attackTarget = enemy.getLocation();
                }
            }
        }
        return attackTarget;
    }

    public static int minDistToTrap(RobotController rc, MapLocation enemyLoc, Direction dir, MapLocation trapLoc) {
        int minDistToTrap = enemyLoc.distanceSquaredTo(trapLoc);
        enemyLoc = enemyLoc.add(dir);
        while (enemyLoc.distanceSquaredTo(trapLoc) < minDistToTrap) {
            minDistToTrap = enemyLoc.distanceSquaredTo(trapLoc);
            enemyLoc = enemyLoc.add(dir);
        }
        return minDistToTrap;
    }

    public static int evaluateTrap(RobotController rc, RobotInfo[] nearbyEnemies, MapLocation trapLocation) {
        int eval = 0;
        for (RobotInfo enemy : nearbyEnemies) {
            MapLocation lastSeenLoc = lastSeenPrevEnemyLoc.getLoc(enemy.getID());
            boolean approxSameDir = false;
            if (lastSeenLoc != null) {
                approxSameDir = (lastSeenLoc.directionTo(enemy.getLocation()) == enemy.getLocation().directionTo(trapLocation))
                                ||(lastSeenLoc.directionTo(enemy.getLocation()).rotateLeft() == enemy.getLocation().directionTo(trapLocation))
                                ||(lastSeenLoc.directionTo(enemy.getLocation()).rotateRight() == enemy.getLocation().directionTo(trapLocation));
            }
            if ((approxSameDir || lastSeenLoc == null || lastSeenLoc.distanceSquaredTo(enemy.getLocation()) > 4)) {
                eval++;
            }
        }
        return eval;
    }

    public static MapLocation chooseTrapLoc(RobotController rc, RobotInfo[] nearbyEnemies, MapLocation[] nearbyAllyTraps, int lowerBound) throws GameActionException {
        MapLocation[] buildableLocs = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 4);
        int bestEval = lowerBound;
        MapLocation bestEvalLoc = null;
        int bestEvalSumDist = Util.BigNum;
        for (MapLocation buildableLoc : buildableLocs) {
            if (rc.canBuild(TrapType.EXPLOSIVE, buildableLoc)) {
                int currEval = evaluateTrap(rc, nearbyEnemies, buildableLoc);

                if (nearbyAllyTraps.length > 0) {
                    int minAllyTrapDist = Util.BigNum;
                    for (MapLocation nearbyAllyTrap : nearbyAllyTraps) {
                        if (nearbyAllyTrap == null) {
                            break;
                        }
                        if (nearbyAllyTrap.distanceSquaredTo(buildableLoc) < minAllyTrapDist) {
                            minAllyTrapDist = nearbyAllyTrap.distanceSquaredTo(buildableLoc);
                        }
                    }
                    if (minAllyTrapDist <= 2) {
                        currEval = 0;
                    }
                }
                int sumDist = 0;
                for (RobotInfo enemy : nearbyEnemies) {
                    sumDist += enemy.getLocation().distanceSquaredTo(buildableLoc);
                }
                if (currEval > bestEval) {
                    bestEval = currEval;
                    bestEvalLoc = buildableLoc;
                    bestEvalSumDist = sumDist;
                } else if (currEval == bestEval) {
                    if (sumDist < bestEvalSumDist) {
                        bestEvalLoc = buildableLoc;
                        bestEvalSumDist = sumDist;
                    }
                }
            }
        }
        return bestEvalLoc;
    }

    public static int evaluateSafety(RobotController rc, MapLocation newLoc,RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) {
        int eval = 0;
        for (RobotInfo nearbyAlly : nearbyAllies) {
            eval += Math.max(0, GameConstants.VISION_RADIUS_SQUARED - nearbyAlly.getLocation().distanceSquaredTo(newLoc));
        }
        for (RobotInfo nearbyEnemy : nearbyEnemies) {
            eval -= Math.max(0, GameConstants.VISION_RADIUS_SQUARED - nearbyEnemy.getLocation().distanceSquaredTo(newLoc));
        }
        return eval;
    }

    public static void fight(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies, MapInfo[] nearbyMapInfos, boolean doEvade) throws GameActionException {
        String indicatorString = "";
        int numNearbyEnemyFlagCarriers = 0;
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.hasFlag()) {
                numNearbyEnemyFlagCarriers++;
            }
        }

        MapLocation[] nearbyAllyTraps = new MapLocation[100];
        int numNearbyAllyTraps= 0;
        for (MapInfo mapInfo : nearbyMapInfos) {
            if (mapInfo.getTrapType() != TrapType.NONE) {
                nearbyAllyTraps[numNearbyAllyTraps] = mapInfo.getMapLocation();
                numNearbyAllyTraps++;
            }
        }



        //chasing
        MapLocation closestEnemyLoc = chooseBestAttackTarget(rc, nearbyEnemies, false);
        if (!isEscorting && !rc.hasFlag() && closestEnemyLoc != null && (nearbyAllies.length-nearbyEnemies.length >= 1 || numNearbyEnemyFlagCarriers > 0)) {
            indicatorString += "chasing " + closestEnemyLoc+"|";
            Navigator.moveToward(rc, closestEnemyLoc);
        } else if (!isEscorting && !rc.hasFlag() && nearbyAllies.length-nearbyEnemies.length <1 && numNearbyEnemyFlagCarriers == 0 && doEvade) {
            //TODO: bait into ally trap
            Direction safestDir = null;
            int safestDirEval = -Util.BigNum;
            for (Direction dir : DirectionsUtil.directions) {
                if (Navigator.canMove(rc, dir)) {
                    int currEval = evaluateSafety(rc, rc.getLocation().add(dir), nearbyAllies, nearbyEnemies);
                    if (currEval > safestDirEval) {
                        safestDirEval = currEval;
                        safestDir = dir;
                    }
                }
            }
            if (safestDir != null) {
                Navigator.tryMove(rc, safestDir);
            }
        }

        //trapping
        MapLocation bestTrapLoc = chooseTrapLoc(rc, nearbyEnemies, nearbyAllyTraps, 3);
        if (bestTrapLoc != null && rc.canBuild(TrapType.EXPLOSIVE, bestTrapLoc)) {
            rc.build(TrapType.EXPLOSIVE, bestTrapLoc);
        }

        //attacking
        MapLocation attackTarget = chooseBestAttackTarget(rc, nearbyEnemies, true);
        if (attackTarget != null) {
            indicatorString+="attack " +attackTarget +"|";
            rc.setIndicatorDot(attackTarget, 200, 0, 0);
        }
        if (attackTarget!= null && rc.canAttack(attackTarget)) {
            rc.attack(attackTarget);
        }

        //healing
        if (rc.getActionCooldownTurns() < 10) {
            int minAllyHealth = 1000;
            MapLocation healTarget = null;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.getHealth() < 1000-80&& ally.getHealth() < minAllyHealth) {
                    minAllyHealth = ally.getHealth();
                    healTarget = ally.getLocation();
                }
            }
            if (healTarget != null && rc.canHeal(healTarget)) {
                rc.heal(healTarget);
            }
        }
//        rc.setIndicatorString(indicatorString);
    }
}
