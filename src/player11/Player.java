package player11;

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
            fight(rc, rc.senseNearbyRobots(-1, rc.getTeam().opponent()), rc.senseNearbyRobots(-1, rc.getTeam()), rc.senseNearbyMapInfos());
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

    public int evaluateSafety(MapLocation newLoc,RobotInfo[] nearbyEnemies,RobotInfo[] nearbyAllies) {
        int eval = 0;
        for (RobotInfo nearbyAlly : nearbyAllies) {
            eval += nearbyAlly.getHealth();
        }
        for (RobotInfo nearbyEnemy : nearbyEnemies) {
            eval -= nearbyEnemy.getHealth();
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
    public void offense(RobotController rc) throws GameActionException{
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos();
        MapLocation[] broadcastFlagLocations = rc.senseBroadcastFlagLocations();

        //fill location if blocked and carrying flag
        if (fillLoc != null) {
            if (rc.canFill(fillLoc)) {
                rc.fill(fillLoc);
                fillLoc = null;
            }
        }

        //pick up flag
        FlagInfo[] nearbyEnemyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());
        for (FlagInfo nearbyEnemyFlag : nearbyEnemyFlags) {
            if (rc.canPickupFlag(nearbyEnemyFlag.getLocation())) {
                rc.pickupFlag(nearbyEnemyFlag.getLocation());
            }
        }

        //go for crumbs
        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(-1); //TODO: only go for crumbs if safe
        MapLocation crumbTarget = Util.chooseClosestLoc(rc.getLocation(), nearbyCrumbs);
        if (crumbTarget != null && nearbyAllies.length-nearbyEnemies.length >= 1) {
            Navigator.moveToward(rc, crumbTarget);
        }

        //fight
        fight(rc, nearbyEnemies, nearbyAllies, nearbyMapInfos);

        //escorting
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
        //carrying flag to spawn or moving to enemy
        if (rc.hasFlag()) {
            lastFlagRound = rc.getRoundNum();
            int flagId = rc.senseNearbyFlags(0)[0].getID();
            MapLocation target = Util.chooseClosestLoc(rc.getLocation(), rc.getAllySpawnLocations());
            fillLoc = Navigator.moveToward(rc, target);
            for (int i = 0; i < 3; i++) {
                if (flagId == Communicator.interpretNumber(rc, Communicator.enemyFlagIDsStart + 14 * i, 14)) {
                    flagInd = i;
                    Communicator.writeLocation(rc, Communicator.allyFlagCarriersStart + i * 12, rc.getLocation());
                    Communicator.writeBit(rc, Communicator.isAllyFlagCarrierAliveStart + i, true);
                }
            }
        } else if (!isEscorting && nearbyEnemies.length==0) {
            MapLocation target = null;
            int minDistSq = Util.BigNum;
            for (int i = 0; i < 3; i++) {
                MapLocation loc = sharedEnemyFlagInfo[i];
                if (loc == null || sharedAllyFlagCarrierInfo[i] != null || Communicator.readBit(rc, Communicator.enemyFlagCapturedStart + i))
                    continue;
                int currDistSq = loc.distanceSquaredTo(rc.getLocation());
                if (currDistSq < minDistSq) {
                    minDistSq = currDistSq;
                    target = loc;
                }
            }
            if (target != null) {
                Navigator.moveToward(rc, target);
            } else {
                if (broadcastFlagLocations.length == 0) {
                    target = targetEnemySpawn(rc);
                    Navigator.moveToward(rc, target);
                } else {
                    MapLocation closestBroadcastLoc = Util.chooseClosestLoc(rc.getLocation(), broadcastFlagLocations);
                    Navigator.moveToward(rc, closestBroadcastLoc);
                }
            }
        }
    }

    public int inAttackRange(MapLocation myLoc, RobotInfo[] nearbyEnemies) {
        int cnt=0;
        for (RobotInfo enemy : nearbyEnemies) {
            if (myLoc.distanceSquaredTo(enemy.getLocation()) <= 4) {
                cnt++;
            }
        }
        return cnt;
    }

    public int inMoveAttackRange(MapLocation myLoc, RobotInfo[] nearbyEnemies) { //is myLoc or any robot one move away from being within 2 tiles?
        int cnt = 0;
        for (RobotInfo enemy : nearbyEnemies) {
            MapLocation newPos = myLoc.add(myLoc.directionTo(enemy.getLocation()));
            if (newPos.distanceSquaredTo(enemy.getLocation()) <= 4) {
                cnt++;
            }
        }
        return cnt;
    }

    public Direction chase(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException{ //this and kite may not return valid dir
        int numMoveAttackRange = inMoveAttackRange(rc.getLocation(),nearbyEnemies);
        if (numMoveAttackRange > 0) { //minimize risk while being able to attack
            int minRisk = Util.BigNum;
            Direction minRiskDir = null;
            for (Direction dir : DirectionsUtil.directions) {
                if (rc.canMove(dir) && inAttackRange(rc.getLocation().add(dir), nearbyEnemies) > 0) {
                    int currRisk = inMoveAttackRange(rc.getLocation().add(dir), nearbyEnemies);
                    if (currRisk < minRisk) {
                        minRisk = currRisk;
                        minRiskDir = dir;
                    }
                }
            }
            return minRiskDir;
        } else {//minimize min dist
            MapLocation closestRobot = Util.chooseClosestRobot(rc.getLocation(), nearbyEnemies);
            if (closestRobot != null && rc.canMove(rc.getLocation().directionTo(closestRobot))) {
                return rc.getLocation().directionTo(closestRobot);
            } else {
                return null;//TODO: minimize dist to allies if no enemy robots in range
            }
        }
    }

    public Direction kite(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies) throws GameActionException {
        int minRisk = Util.BigNum;
        int minDistToAllies = Util.BigNum;
        Direction minDir = null;

        for (Direction dir : DirectionsUtil.directions) {
            if (rc.canMove(dir)) {
                MapLocation newPos = rc.getLocation().add(dir);
                int currRisk = inMoveAttackRange(newPos, nearbyEnemies);
                int currDistToAllies = Util.distanceCombinedRobot(newPos, nearbyAllies);
                if (currRisk < minRisk) {
                    minRisk = currRisk;
                    minDistToAllies = currDistToAllies;
                    minDir = dir;
                } else if (currRisk == minRisk) {
                    if (currDistToAllies < minDistToAllies) {
                        minDistToAllies = currDistToAllies;
                        minDir = dir;
                    }
                }
            }
        }
        return minDir;
    }

    public void kiteWithEval(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies, int eval) throws GameActionException {
        int currAttack = inAttackRange(rc.getLocation(), nearbyEnemies);
        int currMoveAttack = inMoveAttackRange(rc.getLocation(), nearbyEnemies);
        Direction currKite = kite(rc,nearbyEnemies,nearbyAllies);

        if (currKite != null) {
            if (currAttack > 0) {
                rc.move(currKite);
                indicatorString+="kite because under attack "+currKite.name()+",";
            } else if (currMoveAttack == 1) {
                if (rc.getActionCooldownTurns() - 10 < 10 && eval >= 0) {
                    //stay
                } else {
                    rc.move(currKite);
                    indicatorString+="kite because risk "+currKite.name()+",";
                }
            } else if (currMoveAttack > 1) {
                rc.move(currKite);
                indicatorString+="kite because multiple risk "+currKite.name()+",";
            }
        }
    }

    public void chaseWithEval(RobotController rc, RobotInfo[] nearbyEnemies, int eval) throws GameActionException {
        Direction chaseDir = chase(rc, nearbyEnemies);

        Direction origChase=chaseDir;
        if (chaseDir != null) {
            int currAttack = inAttackRange(rc.getLocation().add(chaseDir), nearbyEnemies);
            int currMoveAttack = inMoveAttackRange(rc.getLocation().add(chaseDir), nearbyEnemies);

            if (currMoveAttack == 0) {
                rc.move(chaseDir);
                indicatorString+="chase because safe " + origChase.name() + ",";
            } else if (currAttack == 0 && currMoveAttack <= 1 && eval >= 0) {
                rc.move(chaseDir);
                indicatorString+="chase because overpowering " + origChase.name() + ",";
            }
        }
    }

    public void attack(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies, MapInfo[] nearbyMapInfos) throws GameActionException {
        //trapping
        MapLocation[] nearbyAllyTraps = new MapLocation[81];
        int numNearbyAllyTraps= 0;
        for (MapInfo mapInfo : nearbyMapInfos) {
            if (mapInfo.getTrapType() != TrapType.NONE) {
                nearbyAllyTraps[numNearbyAllyTraps] = mapInfo.getMapLocation();
                numNearbyAllyTraps++;
            }
        }
        MapLocation bestTrapLoc = Traps.chooseTrapLoc(rc, nearbyEnemies, nearbyAllyTraps, 3, lastSeenPrevEnemyLoc);
        if (bestTrapLoc != null && rc.canBuild(TrapType.EXPLOSIVE, bestTrapLoc)) {
            rc.build(TrapType.EXPLOSIVE, bestTrapLoc);
        }

        //attacking micro
        int eval = evaluateSafety(rc.getLocation(), nearbyEnemies, nearbyAllies);

        if (rc.getActionCooldownTurns() < 10) {
            if (inAttackRange(rc.getLocation(), nearbyEnemies) == 0) {
                chaseWithEval(rc,nearbyEnemies,eval);
            }

            MapLocation attackTarget = chooseBestAttackTarget(rc, nearbyEnemies, true);
            if (attackTarget!= null && rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
                indicatorString+="attack,";
            }

            kiteWithEval(rc,nearbyEnemies,nearbyAllies,eval);
        } else {
            kiteWithEval(rc,nearbyEnemies,nearbyAllies,eval);
            if (rc.getMovementCooldownTurns() < 10 && inMoveAttackRange(rc.getLocation(),nearbyEnemies)==0) {
                if (rc.getActionCooldownTurns()-10 < 10) {
                    chaseWithEval(rc,nearbyEnemies,eval);
                } else {
                    Direction chaseDir = chase(rc, nearbyEnemies);
                    Direction origChase=chaseDir;
                    if (chaseDir != null) {
                        int currMoveAttack = inMoveAttackRange(rc.getLocation().add(chaseDir), nearbyEnemies);

                        if (currMoveAttack == 0) {
                            indicatorString+="chase because no attack and safe " + origChase.name()+",";
                            rc.move(chaseDir);
                        }
                    }
                }
            }
        }
        if (rc.getMovementCooldownTurns()<10){
            indicatorString+="stay";
        }
    }

    public void fight(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies, MapInfo[] nearbyMapInfos) throws GameActionException {
        String indicatorString = "";
        int numNearbyEnemyFlagCarriers = 0;
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.hasFlag()) {
                numNearbyEnemyFlagCarriers++;
            }
        }



        //chasing enemy flag carrier
        MapLocation closestEnemyLoc = chooseBestAttackTarget(rc, nearbyEnemies, false);
        if (!isEscorting && !rc.hasFlag() && closestEnemyLoc != null && numNearbyEnemyFlagCarriers > 0) {//TODO: don't chase if can attack
            indicatorString += "chasing " + closestEnemyLoc+"|";
            Navigator.moveToward(rc, closestEnemyLoc);
        }

        attack(rc, nearbyEnemies, nearbyAllies, nearbyMapInfos);


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
