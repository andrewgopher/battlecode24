package player11;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;

public class Util {
    static int BigNum = 10000000;

    public static Team intToTeam(int t) {
        if (t==1) {
            return Team.A;
        } else if (t==2) {
            return Team.B;
        } else {
            return Team.NEUTRAL;
        }
    }

    public static boolean onBorder(RobotController rc) {
        return rc.getLocation().x ==0  || rc.getLocation().x==rc.getMapWidth()-1 ||rc.getLocation().y ==0  || rc.getLocation().y==rc.getMapHeight()-1;
    }

    public static int distanceCombined(MapLocation loc, MapLocation[] locs) {
        int result = 0;
        for (MapLocation currLoc : locs) {
            result += loc.distanceSquaredTo(currLoc);
        }
        return result;
    }

    public static int distanceCombinedRobot(MapLocation loc, RobotInfo[] locs) {
        int result = 0;
        for (RobotInfo currLoc : locs) {
            result += loc.distanceSquaredTo(currLoc.getLocation());
        }
        return result;
    }



    public static MapLocation clampLoc(RobotController rc, MapLocation loc) {
        int x = loc.x;
        int y = loc.y;
        if (x < 0) {
            x = 0;
        }
        if (x>=rc.getMapWidth()) {
            x = rc.getMapWidth()-1;
        }
        if (y<0) {
            y=0;
        }
        if (y>=rc.getMapHeight()) {
            y=rc.getMapHeight()-1;
        }
        return new MapLocation(x,y);
    }
    public static MapLocation chooseClosestRobot(MapLocation loc, RobotInfo[] robots) {
        MapLocation target = null;
        int minDistSq = Util.BigNum;
        for (RobotInfo robot : robots) {
            MapLocation currLoc = robot.getLocation();
            if (currLoc == null) continue;
            int currDistSq =loc.distanceSquaredTo(currLoc);
            if (currDistSq < minDistSq) {
                minDistSq = currDistSq;
                target = currLoc;
            }
        }
        return target;
    }

    public static MapLocation chooseClosestLoc(MapLocation loc, MapLocation[] locs) {
        MapLocation closestLoc = null;
        int minDistSq = Util.BigNum;
        for (MapLocation currLoc: locs) {
            if (currLoc == null) continue;
            int currDistSq =loc.distanceSquaredTo(currLoc);
            if (currDistSq < minDistSq) {
                minDistSq = currDistSq;
                closestLoc = currLoc;
            }
        }
        return closestLoc;
    }
}
