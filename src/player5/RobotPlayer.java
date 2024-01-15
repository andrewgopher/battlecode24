package player5;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    // IF YOU HAVE ANY ARBITRARILY CHOSEN CONSTANTS FOR THE BOT, PUT THEM HERE AS FINAL VARIABLES HERE
    // SO THAT WE CAN MORE EASILY TUNE THEM.

    // END ARBITRARY CONSTANTS

    static int turnCount = 0;
    static Random rng;
    static MapLocation[] sharedAllyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedEnemyFlagInfo =  new MapLocation[3];
    static char[][] mapRecord;

    static MapLocation bottomLeft = new MapLocation(0,0);
    static MapLocation bottomRight, topRight, topLeft;



    static boolean[] symmetries= {true, true, true}; //rot, hor, vert

    static MapLocation spawnPos;

    static int numDefenders = 10;

    static boolean isDefender = false;

    static GlobalUpgrade[] globalUpgrades = {GlobalUpgrade.ACTION, GlobalUpgrade.HEALING};


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
                        rng = new Random(rc.getID());
                        if (rc.getRoundNum() == 1 && rc.readSharedArray(0) == 0) {
                            Communicator.wipeArray(rc, (1 << 16) - 1);
                            Communicator.writeNumber(rc, Communicator.numDefendersStart, 0, 6);
                        }
                        spawnPos = rc.getLocation();
                        mapRecord = new char[rc.getMapWidth()][rc.getMapHeight()];
                        topLeft = new MapLocation(0, rc.getMapHeight() - 1);
                        topRight = new MapLocation(rc.getMapWidth() - 1, rc.getMapHeight() - 1);
                        bottomRight = new MapLocation(rc.getMapWidth() - 1, 0);
                        if (Communicator.getNumDefenders(rc) < numDefenders) {
                            Communicator.incrementDefenders(rc);
                            isDefender = true;
                        }
                    } else {
                        Clock.yield();
                        continue;
                    }
                }

                Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo);
                indicateSharedInfo(rc);
                Communicator.updateEnemyFlagLocations(rc, sharedEnemyFlagInfo);

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
                } else if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS) {
                    MapLocation target = targetEnemySpawn(rc);
                    Navigator.moveToward(rc, target);
                } else {
                    attack(rc);
                }


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
    }

    static MapLocation newFlagLoc;

    static MapLocation secondNewFlagLoc;

    static int flagRole = 0;

    public static MapLocation getFlagLocRole(MapLocation corner) {
        if (corner.equals(bottomLeft)) {
            if (flagRole == 1) {
                return new MapLocation(corner.x, corner.y+6);
            } else if (flagRole == 2) {
                return new MapLocation(corner.x+6, corner.y);
            }
            return corner;
        } else if (corner.equals(bottomRight)) {
            if (flagRole == 1) {
                return new MapLocation(corner.x, corner.y+6);
            } else if (flagRole == 2) {
                return new MapLocation(corner.x-6, corner.y);
            }
            return corner;
        } else if (corner.equals(topLeft)) {
            if (flagRole == 1) {
                return new MapLocation(corner.x, corner.y-6);
            } else if (flagRole == 2) {
                return new MapLocation(corner.x+6, corner.y);
            }
            return corner;
        } else {
            if (flagRole == 1) {
                return new MapLocation(corner.x, corner.y-6);
            } else if (flagRole == 2) {
                return new MapLocation(corner.x-6, corner.y);
            }
            return corner;
        }
    }
    public static int distanceCombined(RobotController rc, MapLocation loc, MapLocation[] locs) {
        int result = 0;
        for (MapLocation currLoc : locs) {
            result += loc.distanceSquaredTo(currLoc);
        }
        return result;
    }

    public static void defend(RobotController rc) throws GameActionException {
        pvp(rc, rc.senseNearbyRobots(-1, rc.getTeam().opponent()), rc.senseNearbyRobots(-1, rc.getTeam()));

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
                    rc.pickupFlag(flag.getLocation());
                    MapLocation[] allySpawnLocs = rc.getAllySpawnLocations();
                    int blCorner=distanceCombined(rc, bottomLeft, allySpawnLocs);
                    int brCorner= distanceCombined(rc, bottomRight,allySpawnLocs);
                    int tlCorner= distanceCombined(rc, topLeft, allySpawnLocs);
                    int trCorner=distanceCombined(rc, topRight,allySpawnLocs);
                    MapLocation bestCorner = bottomLeft;
                    int bestCornerDist = blCorner;
                    MapLocation secondCorner = bottomRight;
                    int secondCornerDist = brCorner;
                    if (brCorner < blCorner) {
                        bestCorner = bottomRight;
                        bestCornerDist = brCorner;
                        secondCorner = bottomLeft;
                        secondCornerDist=blCorner;
                    }

                    if (tlCorner < bestCornerDist) {
                        secondCorner = bestCorner;
                        secondCornerDist = bestCornerDist;
                        bestCorner =  topLeft;
                        bestCornerDist = tlCorner;
                    } else if (tlCorner < secondCornerDist) {
                        secondCornerDist = tlCorner;
                        secondCorner = topLeft;
                    }

                    if (trCorner < bestCornerDist) {
                        secondCorner = bestCorner;
                        secondCornerDist = bestCornerDist;
                        bestCorner =  topRight;
                        bestCornerDist = trCorner;
                    } else if (trCorner < secondCornerDist) {
                        secondCornerDist = trCorner;
                        secondCorner = topRight;
                    }

                    if (sharedAllyFlagInfo[0] != null) {
                        if (sharedAllyFlagInfo[1] != null) {
                            flagRole = 2;
                        } else {
                            flagRole = 1;
                        }
                    }
                    newFlagLoc = getFlagLocRole(bestCorner);
                    secondNewFlagLoc = getFlagLocRole(secondCorner);
                    System.out.println(newFlagLoc);
                    Communicator.writeAllyFlagLocation(rc, newFlagLoc);
                }
            }
        }

        int minDistSqToPlacedFlag = 100000;
        MapLocation closestFlag = null;
        for (int i = 0; i < 3;i ++) {
            if (sharedAllyFlagInfo[i] != null) {
                if (sharedAllyFlagInfo[i].distanceSquaredTo(rc.getLocation()) < minDistSqToPlacedFlag) {
                    minDistSqToPlacedFlag = sharedAllyFlagInfo[i].distanceSquaredTo(rc.getLocation());
                    closestFlag = sharedAllyFlagInfo[i];
                }
            }
        }

        if (minDistSqToPlacedFlag <= 4) {
            if (rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                rc.build(TrapType.EXPLOSIVE, rc.getLocation());
            }
        }

        if (rc.getLocation().x %3 == 1 && rc.getLocation().y%3==1) {
            if (minDistSqToPlacedFlag > 2 && minDistSqToPlacedFlag <= 200) {
                if (rc.canBuild(TrapType.WATER, rc.getLocation())) {
                    rc.build(TrapType.WATER, rc.getLocation());
                }
            }
        }
        if (rc.hasFlag()) {
            rc.setIndicatorString(newFlagLoc.toString());
            if (rc.canDropFlag(newFlagLoc)) {
                rc.dropFlag(newFlagLoc);
            } else {
                Navigator.moveToward(rc, newFlagLoc);
            }
        } else {
            if (minDistSqToPlacedFlag > 200 && closestFlag != null) {
                Navigator.moveToward(rc, closestFlag);
            } else {
                Navigator.wander(rc, rng);
            }
        }
    }

    public static void setup(RobotController rc) throws GameActionException {
        MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(-1);
        if (nearbyCrumbs.length > 0) {
            MapLocation target = nearbyCrumbs[0];
            int minDistSq = 1000;
            for (MapLocation nearbyCrumb : nearbyCrumbs) {
                int currDistSq =nearbyCrumb.distanceSquaredTo(rc.getLocation());
                if (currDistSq < minDistSq) {
                    minDistSq = currDistSq;
                    target = nearbyCrumb;
                }
            }
            Navigator.moveToward(rc, target);
        } else {
            Navigator.wander(rc, rng);
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
        MapLocation target = myLoc.add(DirectionsUtil.randomDirection(rng));
        int minDistSq = 1000000;
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
    public static void attack(RobotController rc) throws GameActionException{

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
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
                Communicator.eraseFirstLocation(rc, Communicator.enemyFlagLocationsStart, Communicator.enemyFlagLocationsStart + 3 * 12, rc.getLocation());
            }
        }
        pvp(rc, nearbyEnemies, nearbyAllies);

        if (rc.hasFlag()) {
            MapLocation target = chooseClosestTarget(rc.getLocation(), rc.getAllySpawnLocations());
            fillLoc = Navigator.moveToward(rc, target);
        } else if (!isEscorting && (sharedEnemyFlagInfo[0] != null || sharedEnemyFlagInfo[1] != null || sharedEnemyFlagInfo[2] != null)) {
            MapLocation target = chooseClosestTarget(rc.getLocation(), sharedEnemyFlagInfo);
            Navigator.moveToward(rc, target);
        } else {
            isEscorting = false;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.hasFlag()) {
                    int distSqFromAlly = ally.getLocation().distanceSquaredTo(rc.getLocation());
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
    }

    public static void pvp(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies) throws GameActionException {
        int minEnemyHealth=1001;
        MapLocation attackTarget = rc.getLocation();
        for (RobotInfo enemy : nearbyEnemies) {
            if (enemy.hasFlag() && rc.canAttack(enemy.getLocation())) {
                attackTarget = enemy.getLocation();
                break;
            }
            if (rc.canAttack(enemy.getLocation()) && enemy.getHealth()<minEnemyHealth) {
                minEnemyHealth = enemy.getHealth();
                attackTarget = enemy.getLocation();
            }
        }
        if (nearbyEnemies.length > 0 && rc.canAttack(attackTarget)) {
            rc.attack(attackTarget);
        }
        Navigator.moveToward(rc, attackTarget);
        if (rc.getActionCooldownTurns() < 10) {

            int minAllyHealth = 1000;
            MapLocation healTarget = null;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.getHealth() < 1000 && ally.getHealth() < minAllyHealth) {
                    minAllyHealth = ally.getHealth();
                    healTarget = ally.getLocation();
                }
            }
            if (healTarget != null && rc.canHeal(healTarget)) {
                rc.heal(healTarget);
            }
        }
    }
}
