package player13;

import battlecode.common.*;
import player13.fast.FastIntLocMap;

public class Traps {


    public static int evaluateTrap(RobotController rc, RobotInfo[] nearbyEnemies, MapLocation trapLocation, FastIntLocMap lastSeenPrevEnemyLoc) {
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

    public static MapLocation chooseTrapLoc(RobotController rc, RobotInfo[] nearbyEnemies, MapLocation[] nearbyAllyTraps, int lowerBound, FastIntLocMap lastSeenPrevEnemyLoc, TrapType trapType) throws GameActionException {
        MapLocation[] buildableLocs = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 4);
        int bestEval = lowerBound;
        MapLocation bestEvalLoc = null;
        int bestEvalSumDist = Util.BigNum;


        int minSpacing = 1;
        if (rc.getCrumbs() < trapType.buildCost*10 || nearbyEnemies.length < 7) {
            minSpacing = 2;
        }
        if (rc.getCrumbs() < trapType.buildCost || nearbyEnemies.length < 4) {
            minSpacing = 4;
        }

        for (MapLocation buildableLoc : buildableLocs) {
            if (rc.canBuild(trapType, buildableLoc)) {
                int currEval = evaluateTrap(rc, nearbyEnemies, buildableLoc, lastSeenPrevEnemyLoc);

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
                    if (minAllyTrapDist < minSpacing) {
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
}
