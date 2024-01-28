package player16;

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
//    public boolean isLineBlocked(RobotController rc, MapLocation a, MapLocation b) throws GameActionException{
//        while (!a.equals(b)) {
//            Direction dir = a.directionTo(b);
//            if (rc.canSenseLocation(a.add(dir))) {
//                if (!rc.senseMapInfo(a.add(dir)).isWall()) {
//                    a=a.add(dir);
//                } else {
//                    return true;
//                }
//            } else {
//                break;
//            }
//        }
//        return false;
//    }
    public void offense(RobotController rc) throws GameActionException{
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        int numOpenEnemies = 0;
        for (RobotInfo enemy : nearbyEnemies) {
            if (!Util.isLineBlocked(rc, rc.getLocation(), enemy.getLocation())) {
                numOpenEnemies++;
            }
        }

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos();
        MapLocation[] broadcastFlagLocations = rc.senseBroadcastFlagLocations();

        //fill location if blocked and carrying flag
        if (fillLoc != null) {
            if (rc.canFill(fillLoc)) {
                rc.fill(fillLoc);
                fillLoc = null;
            } else if ((rc.getActionCooldownTurns()>= 10 || rc.getCrumbs() < GameConstants.DIG_COST) && rc.getLocation().distanceSquaredTo(fillLoc)<=2) {
                return;
            } else {
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
        if (!rc.hasFlag()) {
            fight(rc, nearbyEnemies, nearbyAllies, nearbyMapInfos);
        }

        //escorting
        isEscorting = false;
        for (RobotInfo ally : nearbyAllies) {
            int distSqFromAlly = ally.getLocation().distanceSquaredTo(rc.getLocation());

            if (ally.hasFlag()) {
                int numAlreadyEscorting = 0;
                for (RobotInfo ally2 : nearbyAllies) {
                    if (ally2.getLocation().distanceSquaredTo(ally.getLocation()) <= GameConstants.VISION_RADIUS_SQUARED) {
                        numAlreadyEscorting++;
                    }
                }
                if (numAlreadyEscorting < 8) { //TODO: use shared array instead so further away allies can join escort
                    isEscorting = true;
                    Direction direction = ally.getLocation().directionTo(Util.chooseClosestLoc(ally.getLocation(), rc.getAllySpawnLocations()));
                    int distSqFromAllyAfterAllyMoves = ally.getLocation().add(direction).distanceSquaredTo(rc.getLocation());
                    if (distSqFromAllyAfterAllyMoves < distSqFromAlly && ally.getLocation().distanceSquaredTo(rc.getLocation()) < 4) {
                        Navigator.tryMove(rc, direction);
                    }
                    if (distSqFromAllyAfterAllyMoves >= distSqFromAlly) {
                        Navigator.tryMove(rc, direction);
                    }
                    break;
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
        } else if (!isEscorting && numOpenEnemies==0) {
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
                indicatorString+="rush enemy flag,";
                Navigator.moveToward(rc, target);
            } else {
                MapLocation closestBroadcastLoc = Util.chooseClosestLoc(rc.getLocation(), broadcastFlagLocations);
                if (closestBroadcastLoc != null && rc.getLocation().distanceSquaredTo(closestBroadcastLoc) > 10) {
                    Navigator.moveToward(rc, closestBroadcastLoc);
                    indicatorString+="rush broadcast,";
                } else {
                    target = targetEnemySpawn(rc);
                    Navigator.moveToward(rc, target);
                    indicatorString+="rush enemy spawn,";
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
            Direction dir  =myLoc.directionTo(enemy.getLocation());
            MapLocation newPos = myLoc.add(dir);
            if (newPos.distanceSquaredTo(enemy.getLocation()) <= 4) {
                cnt++;
                continue;
            }
            newPos = myLoc.add(dir.rotateLeft());
            if (newPos.distanceSquaredTo(enemy.getLocation()) <= 4) {
                cnt++;
                continue;
            }
            newPos=myLoc.add(dir.rotateRight());
            if (newPos.distanceSquaredTo(enemy.getLocation()) <= 4) {
                cnt++;
                continue;
            }
        }
        return cnt;
    }

    public Direction chase(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
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
            if (closestRobot != null && rc.getMovementCooldownTurns()<10) {
                int minDist = Util.BigNum;
                Direction minDistDir = null;
                for (Direction dir : DirectionsUtil.directions) {
                    int currDist = rc.getLocation().add(dir).distanceSquaredTo(closestRobot);
                    if (Navigator.canPass(rc, dir) && currDist < minDist) {
                        minDistDir = dir;
                        minDist = currDist;
                    }
                }
                return minDistDir;
            }  else {
                return null;
            }//TODO: min dist to allies?
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
                if (currRisk > minRisk) {
                    continue;
                }
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

    public void kiteWithEval(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies) throws GameActionException {
        int currMoveAttack = inMoveAttackRange(rc.getLocation(), nearbyEnemies);
        Direction currKite = kite(rc,nearbyEnemies,nearbyAllies);

        if (currKite != null) {
            if (((currMoveAttack == 1 && rc.getActionCooldownTurns()-10>=10)  || (currMoveAttack>1))&&rc.canMove(currKite)) {
                rc.move(currKite);
                indicatorString+="kite "+currKite.name()+",";
            }
        }
    }

    public void chaseWithEval(RobotController rc, RobotInfo[] nearbyEnemies) throws GameActionException {
        Direction chaseDir = chase(rc, nearbyEnemies);
//        indicatorString+="try chase: "+chaseDir+",";

        if (chaseDir != null) {
            int currMoveAttack = inMoveAttackRange(rc.getLocation().add(chaseDir), nearbyEnemies);

            if ((currMoveAttack <= 1 && rc.getActionCooldownTurns()-10<10) || currMoveAttack==0) {
                Navigator.tryMove(rc,chaseDir);
                indicatorString+="chase " + chaseDir.name() + ",";
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

        if (nearbyAllies.length >= 5) {
            MapLocation bestTrapLoc = Traps.chooseTrapLoc(rc, nearbyEnemies, nearbyAllyTraps, 3, lastSeenPrevEnemyLoc, TrapType.STUN);
            if (bestTrapLoc != null && rc.canBuild(TrapType.STUN, bestTrapLoc)) {
                rc.build(TrapType.STUN, bestTrapLoc);
            }
        } else {
            MapLocation bestTrapLoc = Traps.chooseTrapLoc(rc, nearbyEnemies, nearbyAllyTraps, 3, lastSeenPrevEnemyLoc, TrapType.EXPLOSIVE);
            if (bestTrapLoc != null && rc.canBuild(TrapType.EXPLOSIVE, bestTrapLoc)) {
                rc.build(TrapType.EXPLOSIVE, bestTrapLoc);
            }
        }

        //attacking micro

        if (rc.getActionCooldownTurns() < 10) {
            if (inAttackRange(rc.getLocation(), nearbyEnemies) == 0) {
                Direction chaseDir =chase(rc,nearbyEnemies);
                if (chaseDir != null && Navigator.canMove(rc,chaseDir)) {
                    indicatorString+="chase " + chaseDir+",";
                    Navigator.tryMove(rc,chaseDir);
                }
            }

            MapLocation attackTarget = chooseBestAttackTarget(rc, nearbyEnemies, true);
            if (attackTarget!= null && rc.canAttack(attackTarget)) {
                rc.attack(attackTarget);
                indicatorString+="attack,";

                Direction kiteDir = kite(rc,nearbyEnemies, nearbyAllies);
                if (kiteDir != null && Navigator.canMove(rc,kiteDir)) {
                    indicatorString+="kite " + kiteDir+",";
                    Navigator.tryMove(rc,kiteDir);
                }
            }
        } else {
            kiteWithEval(rc,nearbyEnemies,nearbyAllies);
            chaseWithEval(rc,nearbyEnemies);
        }
        if (rc.getMovementCooldownTurns()<10){
            indicatorString+="no micro movement,";
        }
    }

    public void fight(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies, MapInfo[] nearbyMapInfos) throws GameActionException {
        int numNearbyEnemyFlagCarriers = 0;
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.hasFlag()) {
                numNearbyEnemyFlagCarriers++;
            }
        }



        //chasing enemy flag carrier
        MapLocation bestAttackTarget = chooseBestAttackTarget(rc, nearbyEnemies, false);
        if (!isEscorting && !rc.hasFlag() && bestAttackTarget != null && numNearbyEnemyFlagCarriers > 0) {//TODO: don't chase if can attack
            indicatorString += "chasing " + bestAttackTarget+"|";
            Navigator.moveToward(rc, bestAttackTarget);
        }


        MapLocation closestEnemyLoc = Util.chooseClosestRobot(rc.getLocation(),nearbyEnemies);

//        alternate healing code (this doesn't heal enough)
        if (rc.getActionCooldownTurns() < 10) {
            int currHealCyc = (rc.getActionCooldownTurns() + Util.healLevelCoolDown[rc.getLevel(SkillType.HEAL)]) / 10;
            indicatorString += currHealCyc;
            MapLocation currLoc = rc.getLocation();
            if (closestEnemyLoc != null) {
                for (int i = 0; i < currHealCyc-1; i++) {
                    if (currLoc.distanceSquaredTo(closestEnemyLoc) <= 4) break;
                    int minDist = Util.BigNum;
                    Direction minDistDir = null;
                    for (Direction dir : DirectionsUtil.directions) {
                        MapLocation newLoc = currLoc.add(dir);
                        if (!rc.canSenseLocation(newLoc) || !rc.sensePassability(newLoc) || rc.senseRobotAtLocation(newLoc) != null) {
                            continue;
                        }
                        int currDist = currLoc.add(dir).distanceSquaredTo(closestEnemyLoc);
                        if (currDist < minDist) {
                            minDistDir = dir;
                            minDist = currDist;
                        }
                    }
                    if (minDistDir != null) {
                        currLoc = currLoc.add(minDistDir);
                    }
                    indicatorString += " " + currLoc;
                }
            }
            indicatorString += ",";

            if (closestEnemyLoc == null || currLoc.distanceSquaredTo(closestEnemyLoc) > 4) {
                int minAllyHealth = 1000;
                MapLocation healTarget = null;
                for (RobotInfo ally : nearbyAllies) {
                    if (ally.getHealth() < 1000 && ally.getHealth() < minAllyHealth && rc.canHeal(ally.getLocation())) {
                        minAllyHealth = ally.getHealth();
                        healTarget = ally.getLocation();
                    }
                }
                if (healTarget != null && rc.canHeal(healTarget)) {
                    indicatorString += "heal" + ",";
                    rc.heal(healTarget);
                }
            }
        }
        attack(rc, nearbyEnemies, nearbyAllies, nearbyMapInfos);


    }

    public int getNumSteps(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x-b.x),Math.abs(a.y-b.y));
    }

    public int stepsToAttack(RobotController rc, MapLocation a, MapLocation b) throws GameActionException {
        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(b, 4);
        int minSteps = Util.BigNum;
        for (MapLocation loc : locs) {
            int currSteps = getNumSteps(a,loc);
            if (currSteps < minSteps) {
                minSteps = currSteps;
            }
        }
        return minSteps;
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
