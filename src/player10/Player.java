package player10;

import battlecode.common.*;

public class Player extends Robot{
    static boolean isEscorting = false;

    static int flagInd = -1;
    static int lastFlagRound = -1;

    @Override
    public void initTurn(RobotController rc) throws  GameActionException {
        super.initTurn(rc);
        if (!rc.hasFlag() && flagInd != -1 && rc.getRoundNum()-lastFlagRound <= 1 && rc.senseMapInfo(rc.getLocation()).getSpawnZoneTeamObject() == rc.getTeam()) {
            Communicator.writeBit(rc, Communicator.enemyFlagCapturedStart+flagInd,true);
            flagInd = -1;
            lastFlagRound=-1;
        }
    }


    static MapLocation newFlagLoc;

    @Override
    public void spawn(RobotController rc, MapLocation mySpawnLoc) throws  GameActionException {
        super.spawn(rc,mySpawnLoc);
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1, rc.getTeam());
        for (FlagInfo flag : nearbyFlags) {
            if (!flag.isPickedUp() && rc.canPickupFlag(flag.getLocation())) {
                boolean isUntouched = true;
                for (int i = 0; i < 3; i++) {
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
    }

    public void defend(RobotController rc) throws GameActionException {
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

    public MapLocation getClosestPlacedAllyFlag(RobotController rc) {
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

    public MapLocation targetEnemySpawn(RobotController rc) {
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


    public int minDistToTrap(RobotController rc, MapLocation enemyLoc, Direction dir, MapLocation trapLoc) {
        int minDistToTrap = enemyLoc.distanceSquaredTo(trapLoc);
        enemyLoc = enemyLoc.add(dir);
        while (enemyLoc.distanceSquaredTo(trapLoc) < minDistToTrap) {
            minDistToTrap = enemyLoc.distanceSquaredTo(trapLoc);
            enemyLoc = enemyLoc.add(dir);
        }
        return minDistToTrap;
    }

    public int evaluateTrap(RobotController rc, RobotInfo[] nearbyEnemies, MapLocation trapLocation) {
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

    public MapLocation chooseTrapLoc(RobotController rc, RobotInfo[] nearbyEnemies, MapLocation[] nearbyAllyTraps, int lowerBound) throws GameActionException {
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

    public int evaluateSafety(RobotController rc, MapLocation newLoc,RobotInfo[] nearbyAllies, RobotInfo[] nearbyEnemies) {
        int eval = 0;
        for (RobotInfo nearbyAlly : nearbyAllies) {
            eval += Math.max(0, GameConstants.VISION_RADIUS_SQUARED - nearbyAlly.getLocation().distanceSquaredTo(newLoc));
        }
        for (RobotInfo nearbyEnemy : nearbyEnemies) {
            eval -= Math.max(0, GameConstants.VISION_RADIUS_SQUARED - nearbyEnemy.getLocation().distanceSquaredTo(newLoc));
        }
        return eval;
    }

    public MapLocation chooseBestAttackTarget(RobotController rc, RobotInfo[] nearbyEnemies, boolean mustBeAttackable) throws GameActionException {
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
    public void attack(RobotController rc) throws GameActionException{
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
        MapLocation crumbTarget = Util.chooseClosestLoc(rc.getLocation(), nearbyCrumbs);
        if (crumbTarget != null && nearbyAllies.length-nearbyEnemies.length >= 1) {
            Navigator.moveToward(rc, crumbTarget);
        }
        fight(rc, nearbyEnemies, nearbyAllies, nearbyMapInfos, true);

        isEscorting = false;
        for (RobotInfo ally : nearbyAllies) {
            int distSqFromAlly = ally.getLocation().distanceSquaredTo(rc.getLocation());
            if (ally.hasFlag()) {
                isEscorting = true;
                Direction direction = ally.getLocation().directionTo(Util.chooseClosestLoc(ally.getLocation(), rc.getAllySpawnLocations()));
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
            int flagId = rc.senseNearbyFlags(0)[0].getID();
            MapLocation target = Util.chooseClosestLoc(rc.getLocation(), rc.getAllySpawnLocations());
            fillLoc = Navigator.moveToward(rc, target);
            for (int i = 0; i < 3; i ++) {
                if (flagId == Communicator.interpretNumber(rc, Communicator.enemyFlagIDsStart+14*i, 14)) {
                    flagInd = i;
                    Communicator.writeLocation(rc, Communicator.allyFlagCarriersStart+i*12, rc.getLocation());
                    Communicator.writeBit(rc, Communicator.isAllyFlagCarrierAliveStart+i, true);
                }
            }
        } else if (!isEscorting) {
            MapLocation target = null;
            int minDistSq = Util.BigNum;
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
            } else {
                if (broadcastFlagLocations.length==0) {
                    target = targetEnemySpawn(rc);
                    Navigator.moveToward(rc, target);
                } else {
                    MapLocation closestBroadcastLoc = Util.chooseClosestLoc(rc.getLocation(), broadcastFlagLocations);
                    Navigator.moveToward(rc, closestBroadcastLoc);
                }
            }
        }
    }
    public void fight(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies, MapInfo[] nearbyMapInfos, boolean doEvade) throws GameActionException {
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



        //chasing, evading
        MapLocation closestEnemyLoc = chooseBestAttackTarget(rc, nearbyEnemies, false);
        if (!isEscorting && !rc.hasFlag() && closestEnemyLoc != null && (nearbyAllies.length-nearbyEnemies.length >= 1 || numNearbyEnemyFlagCarriers > 0)) {//TODO: don't chase if can attack
            indicatorString += "chasing " + closestEnemyLoc+"|";
            Navigator.moveToward(rc, closestEnemyLoc);
        } else if (!isEscorting && !rc.hasFlag() && nearbyAllies.length-nearbyEnemies.length <1 && numNearbyEnemyFlagCarriers == 0 && doEvade) { //TODO: evading AFTER attack
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
        if (rc.getActionCooldownTurns() < 10 && (closestEnemyLoc == null || closestEnemyLoc.distanceSquaredTo(rc.getLocation()) >= 16)) { //TODO: tune
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
        rc.setIndicatorString(indicatorString);
    }

    public void setup(RobotController rc) throws GameActionException {
        MapLocation closestFlag = getClosestPlacedAllyFlag(rc);
        int minDistSqToPlacedFlag = Util.BigNum;
        if (closestFlag != null) {
            minDistSqToPlacedFlag = rc.getLocation().distanceSquaredTo(closestFlag);
        }
        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(-1);
        MapLocation target = Util.chooseClosestLoc(rc.getLocation(), nearbyCrumbs);
        if (target != null) {
            Navigator.moveToward(rc, target);
        } else {
            Navigator.wander(rc,rng);
        }
    }
}
