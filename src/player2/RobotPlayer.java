package player2;

import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
    // IF YOU HAVE ANY ARBITRARILY CHOSEN CONSTANTS FOR THE BOT, PUT THEM HERE AS FINAL VARIABLES HERE
    // SO THAT WE CAN MORE EASILY TUNE THEM.

    static final int numAttackersPerFlag = 3;

    // END ARBITRARY CONSTANTS

    static int turnCount = 0;
    static Random rng;
    static MapLocation[] sharedAllyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedEnemyFlagInfo =  new MapLocation[3];
    static char[][] mapRecord;



    static boolean[] symmetries= {true, true, true}; //rot, hor, vert

    static MapLocation spawnPos;


    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            try {
                if (!rc.isSpawned()){//spawn
                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    MapLocation mySpawnLoc = spawnLocs[rc.getID()%spawnLocs.length];
                    if (rc.canSpawn(mySpawnLoc)) {
                        rc.spawn(mySpawnLoc);
                        rng = new Random(rc.getID());
                        if (rc.getRoundNum() == 1 && rc.readSharedArray(0) == 0) {
                            Communicator.wipeArray(rc, (1<<16)-1);
                        }
                        spawnPos = rc.getLocation();
                        mapRecord = new char[rc.getMapWidth()][rc.getMapHeight()];
                    } else {
                        Clock.yield();
                        continue;
                    }
                }
                if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS) {
                    setup(rc);
                } else {
                    main(rc);
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


    public static void setup(RobotController rc) throws GameActionException {
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo);
        indicateSharedInfo(rc);


        Communicator.updateEnemyFlagLocations(rc, sharedEnemyFlagInfo);

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
                    newFlagLoc = rc.getLocation();
                }
            }
        }
        if (rc.hasFlag()) {
            if (rc.canDropFlag(newFlagLoc)) {
                rc.dropFlag(newFlagLoc);
                Communicator.writeAllyFlagLocation(rc, newFlagLoc);
            } else {
                Navigator.moveToward(rc, newFlagLoc);
            }
        } else {
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


    public static MapLocation chooseClosestTarget(RobotController rc, MapLocation[] locs) {
        MapLocation target = rc.getLocation().add(DirectionsUtil.randomDirection(rng));
        int minDistSq = 1000000;
        for (MapLocation loc: locs) {
            if (loc  == null) continue;
            int currDistSq =loc.distanceSquaredTo(rc.getLocation());
            if (currDistSq < minDistSq) {
                minDistSq = currDistSq;
                target = loc;
            }
        }
        return target;
    }

    public static void main(RobotController rc) throws GameActionException{
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo);
        indicateSharedInfo(rc);

        Communicator.updateEnemyFlagLocations(rc, sharedEnemyFlagInfo);


        if (rc.canPickupFlag(rc.getLocation())) {
            rc.pickupFlag(rc.getLocation());
            Communicator.eraseFirstLocation(rc, Communicator.enemyFlagLocationsStart, Communicator.enemyFlagLocationsStart+3*12,rc.getLocation());
        }
        if (rc.hasFlag()) {
            MapLocation target = chooseClosestTarget(rc, rc.getAllySpawnLocations());
            Navigator.moveToward(rc, target);
        } else if (sharedEnemyFlagInfo[0] != null || sharedEnemyFlagInfo[1] != null || sharedEnemyFlagInfo[2] != null) {
            MapLocation target = chooseClosestTarget(rc, sharedEnemyFlagInfo);
            Navigator.moveToward(rc, target);
        } else{
            Symmetry currSymmetry = getSymmetry();

            MapLocation target;

            if (currSymmetry == Symmetry.ROTATIONAL) {
                target = new MapLocation(rc.getMapWidth() - spawnPos.x, rc.getMapHeight() - spawnPos.y);
            } else if (currSymmetry == Symmetry.HORIZONTAL) {

                target = new MapLocation(spawnPos.x, rc.getMapHeight() - spawnPos.y);
            } else {
                target = new MapLocation(rc.getMapWidth() - spawnPos.x, spawnPos.y);

            }
            Navigator.moveToward(rc, target);
        }

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        int minDistSq=1000;
        MapLocation attackTarget = rc.getLocation();
        for (RobotInfo enemy : nearbyEnemies) {
            int currDistSq = (enemy.getLocation().distanceSquaredTo(rc.getLocation()));
            if (currDistSq < minDistSq) {
                minDistSq = currDistSq;
                attackTarget = enemy.getLocation();
            }
        }
        if (nearbyEnemies.length > 0 && rc.canAttack(attackTarget)) {
            rc.attack(attackTarget);
        }
    }
}
