package player9;

import battlecode.common.*;

import java.util.Random;

public strictfp class Navigator {
    public static boolean canMove(RobotController rc, Direction dir) throws GameActionException {
        MapLocation newLoc = rc.getLocation().add(dir);
        return rc.canMove(dir) || (rc.onTheMap(newLoc) && rc.senseMapInfo(newLoc).isWater());
    }

    public static MapLocation tryMove(RobotController rc, Direction dir) throws GameActionException {
        MapLocation newLoc = rc.getLocation().add(dir);
        if (rc.onTheMap(newLoc) && rc.senseMapInfo(newLoc).isWater()) {
            if (rc.hasFlag()) {
                if (rc.canDropFlag(rc.getLocation())) {
                    rc.dropFlag(rc.getLocation());
                    return newLoc;
                }
            } else {
                if (rc.canFill(newLoc)) {
                    rc.fill(newLoc);
                }
            }
        }
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
        return null;
    }

    public static MapLocation tryDir(RobotController rc, Direction dir) throws GameActionException {
        if (canMove(rc,dir)) {
            return tryMove(rc,dir);
        } else {
            MapLocation nextLoc = rc.getLocation().add(dir);

            if (rc.onTheMap(nextLoc)) {
                MapInfo mapInfo = rc.senseMapInfo(nextLoc);
                if (mapInfo.isDam()) {
                    return null;
                }
            } else {
                return null;
            }
            int i  =0;
            while (!canMove(rc,dir) && i < 8) {
                dir= dir.rotateRight();
                i++;
            }
            return tryMove(rc,dir);
        }
    }

    public static MapLocation moveToward(RobotController rc,MapLocation loc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return null;
        }
        return tryDir(rc, rc.getLocation().directionTo(loc));
    }


    public static void wander(RobotController rc, Random rng) throws GameActionException {
        int currDirInd = rng.nextInt(DirectionsUtil.directions.length);
        for (int i = 0; i < DirectionsUtil.directions.length; i ++) {
            if (rc.canMove(DirectionsUtil.directions[(currDirInd+i)%DirectionsUtil.directions.length])) {
                rc.move(DirectionsUtil.directions[(currDirInd+i)%DirectionsUtil.directions.length]);
                break;
            }
        }
    }
    public static void moveAway(RobotController rc,MapLocation loc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }
        tryDir(rc, rc.getLocation().directionTo(loc).opposite());
    }
}
