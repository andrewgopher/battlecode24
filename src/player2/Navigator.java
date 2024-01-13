package player2;

import battlecode.common.*;

import java.util.Random;

public strictfp class Navigator {

    public static void moveToward(RobotController rc,MapLocation loc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }
        Direction dirToLoc = rc.getLocation().directionTo(loc);
        if (rc.canMove(dirToLoc)) {
            rc.setIndicatorString(dirToLoc.name());
            rc.move(dirToLoc);
        } else {
            if (rc.canFill(rc.getLocation().add(dirToLoc))) {
                rc.fill(rc.getLocation().add(dirToLoc));
            }
            int i  =0;
            while (!rc.canMove(dirToLoc) && i < 8) {
                dirToLoc = dirToLoc.rotateRight();
                i++;
            }
            if (rc.canMove(dirToLoc)) {
                rc.move(dirToLoc);
            }
        }
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
        Direction dirAwayLoc = rc.getLocation().directionTo(loc).opposite();
        if (rc.canMove(dirAwayLoc)) {
            rc.setIndicatorString(dirAwayLoc.name());
            rc.move(dirAwayLoc);
        } else {
            while (!rc.canMove(dirAwayLoc)) {
                dirAwayLoc = dirAwayLoc.rotateRight();
            }
            rc.move(dirAwayLoc);
        }
    }
}
