package player6;

import battlecode.common.*;

import java.util.Random;

public strictfp class Navigator {


    public static MapLocation tryDir(RobotController rc, Direction dir) throws GameActionException {
        if (rc.canMove(dir)) {
            rc.move(dir);
        } else {
            MapLocation nextLoc = rc.getLocation().add(dir);

            if (rc.onTheMap(nextLoc)) {
                MapInfo mapInfo = rc.senseMapInfo(nextLoc);
                if (mapInfo.isDam()) {
                    return null;
                }
                if (mapInfo.isWater() && rc.hasFlag()) {
                    if (rc.canDropFlag(rc.getLocation())) {
                        rc.dropFlag(rc.getLocation());
                        return nextLoc;
                    }
                }
            }
            if (rc.canFill(nextLoc)) {
                rc.fill(nextLoc);
            }
            int i  =0;
            while ((!rc.canMove(dir) && !rc.canFill(rc.getLocation().add(dir)))&& i < 8) {
                dir= dir.rotateRight();
                i++;
            }
            if (rc.canFill(rc.getLocation().add(dir))) {
                rc.fill(rc.getLocation().add(dir));
            }
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        }
        return null;
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
