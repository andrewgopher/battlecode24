package player17;

import battlecode.common.*;

public strictfp class RobotPlayer {
    // IF YOU HAVE ANY ARBITRARILY CHOSEN CONSTANTS FOR THE BOT, PUT THEM HERE AS FINAL VARIABLES HERE
    // SO THAT WE CAN MORE EASILY TUNE THEM.

    // END ARBITRARY CONSTANTS

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        Player player = new Player();
        while (true) {
            try {

                if (!rc.isSpawned()) {//spawn
                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    MapLocation mySpawnLoc = spawnLocs[rc.getID() % spawnLocs.length];
                    if (rc.canSpawn(mySpawnLoc)) {
                        player.spawn(rc, mySpawnLoc);
                    } else {
                        Clock.yield();
                        continue;
                    }
                }
                player.initTurn(rc);
                if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS - 35) {
                    if (Player.isDefender) {
                        player.defend(rc);
                    } else {
                        player.setup(rc);
                    }
                } else if (rc.getRoundNum() < GameConstants.SETUP_ROUNDS) {//TODO: move this into setup
                    boolean isAtDam = false;
                    for (Direction direction : DirectionsUtil.directions) {
                        if (rc.canSenseLocation(rc.getLocation().add(direction))) {
                            if (rc.senseMapInfo(rc.getLocation().add(direction)).isDam()) {
                                isAtDam=true;
                                break;
                            }
                        }
                    }
                    MapLocation target = player.getClosestEnemySpawn(rc,rc.getLocation());
                    Direction dirToSpawn = rc.getLocation().directionTo(target);
                    boolean isBlockedByRobot = false;
                    if (rc.canSenseLocation(rc.getLocation().add(dirToSpawn))) {
                        if (rc.senseRobotAtLocation(rc.getLocation().add(dirToSpawn)) != null) {
                            isBlockedByRobot=true;
                        }
                    }
                    if (!isAtDam) {
                        Navigator.moveToward(rc, target);
                    }
                } else {
                    player.offense(rc);
                }

                player.endTurn(rc);
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



}
