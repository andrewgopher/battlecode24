package player1;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public strictfp class RobotPlayer {
    // IF YOU HAVE ANY ARBITRARILY CHOSEN CONSTANTS FOR THE BOT, PUT THEM HERE AS FINAL VARIABLES HERE
    // SO THAT WE CAN MORE EASILY TUNE THEM.

    // END ARBITRARY CONSTANTS

    static int turnCount = 0;
    static final Random rng = new Random(69420);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            try {
                if (!rc.isSpawned()){//spawn
                    MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                    MapLocation mySpawnLoc = spawnLocs[rc.getID()%spawnLocs.length];
                    if (rc.canSpawn(mySpawnLoc)) rc.spawn(mySpawnLoc);
                }
                else{
                    if (rc.canPickupFlag(rc.getLocation())){ // pick up flag if possible
                        rc.pickupFlag(rc.getLocation());
                        rc.setIndicatorString("Holding a flag!");
                    }
                    if (rc.hasFlag() && rc.getRoundNum() >= GameConstants.SETUP_ROUNDS){
                        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                        MapLocation firstLoc = spawnLocs[0];
                        Direction dir = rc.getLocation().directionTo(firstLoc);
                        if (rc.canMove(dir)) rc.move(dir);
                    }
                    // Move and attack randomly if no objective.
                    Direction dir = directions[rng.nextInt(directions.length)];
                    MapLocation nextLoc = rc.getLocation().add(dir);
                    if (rc.canMove(dir)){
                        rc.move(dir);
                    }
                    else if (rc.canAttack(nextLoc)){
                        rc.attack(nextLoc);
                    }

                    // Rarely attempt placing traps behind the robot.
                    MapLocation prevLoc = rc.getLocation().subtract(dir);
                    if (rc.canBuild(TrapType.EXPLOSIVE, prevLoc) && rng.nextInt() % 37 == 1)
                        rc.build(TrapType.EXPLOSIVE, prevLoc);
                    updateEnemyRobots(rc);
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
    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            // Let the rest of our team know how many enemy robots we see!
            if (rc.canWriteSharedArray(0, enemyRobots.length)){
                rc.writeSharedArray(0, enemyRobots.length);
                int numEnemies = rc.readSharedArray(0);
            }
        }
    }
}
