package player20;

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
                Player.spawnIndicatorString ="";

                if (!rc.isSpawned()) {//spawn
                    player.processSharedArray(rc);

//                    int i = 0;
//                    for (MapLocation loc :Player.sharedAllySpawnInfo) {
//                        if (loc != null) {
//                            Player.spawnIndicatorString += loc + " " + Player.sharedAllySpawnTurnInfo[i]+ " " + Player.sharedAllySpawnEnemiesInfo[i]+",";
//                        }
//                        i++;
//                        if (i==1) break;
//                    }

                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
//                    MapLocation mySpawnLoc = spawnLocs[rc.getID() % spawnLocs.length];
                    MapLocation bestSpawnLoc = null;

                    int maxTurnsSinceReport = 0;

                    double maxEnemiesAtSpawn = 0;

                    for (MapLocation loc : spawnLocs) {
                        if (!rc.canSpawn(loc)) continue;
                        boolean better=false;

                        boolean isCenter=false;
                        int centerInd = -1;

                        int i = 0;
                        for (MapLocation centerLoc : Player.sharedAllySpawnInfo) {
                            if (centerLoc != null && centerLoc.equals(loc)) {
                                isCenter = true;
                            }
                            if (centerLoc != null && centerLoc.isAdjacentTo(loc)) {
                                centerInd = i;
                                break;
                            }
                            i++;
                        }
                        if (centerInd == -1) {
                            bestSpawnLoc = spawnLocs[rc.getID()%spawnLocs.length];
                            break;
                        }
                        if (!isCenter && rc.canSpawn(Player.sharedAllySpawnInfo[centerInd])) {
                            continue;
                        }


                        if (rc.getRoundNum()-Player.sharedAllySpawnTurnInfo[centerInd]>= 15) {
                            better = true;
                        }

                        int currEnemies = Player.sharedAllySpawnEnemiesInfo[centerInd];
                        int currAllies = Communicator.interpretNumber(rc,Communicator.allySpawnsAllies+12*centerInd,12);

                        if (maxTurnsSinceReport < 15) {
                            if ((double)currEnemies/(double)currAllies > maxEnemiesAtSpawn || ((double)currEnemies/(double)currAllies == maxEnemiesAtSpawn && rc.getRoundNum() - Player.sharedAllySpawnTurnInfo[centerInd] >= maxTurnsSinceReport)) {
                                better = true;
                            }
                        }

                        if (better) {
                            bestSpawnLoc = loc;
                            maxTurnsSinceReport = rc.getRoundNum()-Player.sharedAllySpawnTurnInfo[centerInd];
                            maxEnemiesAtSpawn = (double) currEnemies/(double)currAllies;
                        }
                    }


                    if (rc.canSpawn(bestSpawnLoc)) {
                        Player.spawnIndicatorString += "spawn "+ bestSpawnLoc + " turns " + maxTurnsSinceReport+ " enemies " +maxEnemiesAtSpawn +",";
                        player.spawn(rc, bestSpawnLoc);
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
                    //go for crumbs
                    MapLocation[] nearbyCrumbs = rc.senseNearbyCrumbs(-1);
                    MapLocation crumbTarget = Util.chooseClosestLoc(rc.getLocation(), nearbyCrumbs);
                    if (crumbTarget != null) {
                        Navigator.moveToward(rc, crumbTarget);
                    } else {
                        if (!isAtDam) {
                            Navigator.moveToward(rc, target);
                        }
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
