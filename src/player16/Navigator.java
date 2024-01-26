package player16;

import battlecode.common.*;

import java.util.Random;

public class Navigator {
    private static final int PRV_LENGTH = 60;
    private static Direction[] prv = new Direction[PRV_LENGTH];
    private static int pathingCnt = 0;
    private static MapLocation lastPathingTarget = null;
    private static MapLocation lastLocation = null;
    private static int stuckCnt = 0;
    private static int lastPathingTurn = 0;
    private static int currentTurnDir = 0; //TODO: random
    public static int disableTurnDirRound = 0;

    private static Direction[] prv_ = new Direction[PRV_LENGTH];
    private static int pathingCnt_ = 0;
    static int MAX_DEPTH = 15;

    private static Random rng = new Random(69420);

    public static boolean passable(RobotController rc, MapLocation loc) throws GameActionException {
        if (!rc.canSenseLocation(loc)) {
            return true;
        }
        return (rc.sensePassability(loc) || (rc.onTheMap(loc) && rc.senseMapInfo(loc).isWater())) && rc.senseRobotAtLocation(loc) == null;
    }
    static boolean canPass(RobotController rc, MapLocation loc) throws GameActionException {
        if (!rc.onTheMap(loc)) {
            return false;
        }
        if (loc.equals(rc.getLocation())) return true;
        if (!passable(rc, loc)) return false;
        if (!rc.canSenseLocation(loc)) return true;
        return true;
    }

    static boolean canPass(RobotController rc, Direction dir) throws GameActionException {
        MapLocation loc = rc.getLocation().add(dir);
        if (!rc.onTheMap(loc)) {
            return false;
        }
        if (!passable(rc, loc)) return false;
        return true;
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

    public static boolean canMove(RobotController rc, Direction dir) throws GameActionException {
        MapLocation newLoc = rc.getLocation().add(dir);
        return rc.canMove(dir) || (rc.onTheMap(newLoc) && rc.senseMapInfo(newLoc).isWater());
    }

    static MapLocation moveToward(RobotController rc, MapLocation location) throws GameActionException {
        int turnCount = rc.getRoundNum();
        // reset queue when target location changes or there's gap in between calls
        if (!location.equals(lastPathingTarget) || lastPathingTurn < turnCount - 4) {
            pathingCnt = 0;
            stuckCnt = 0;
        }
        MapLocation fillLoc = null;
        if (rc.isMovementReady()) {
            // we increase stuck count only if it's a new turn (optim for empty carriers)
            if (rc.getLocation().equals(lastLocation)) {
                if (turnCount != lastPathingTurn) {
                    stuckCnt++;
                }
            } else {
                lastLocation = rc.getLocation();
                stuckCnt = 0;
            }
            lastPathingTarget = location;
            lastPathingTurn = turnCount;
            if (stuckCnt >= 3) {
                wander(rc, rng);
                pathingCnt = 0;
            }
//            if (stuckCnt >= 10) {
//                // make sure if it's a carrier on a well, wait 40 turns
//                do {
//                    if (rc.getType() == RobotType.CARRIER && rc.getWeight() == GameConstants.CARRIER_CAPACITY) {
//                        if (rc.senseWell(rc.getLocation()) != null || stuckCnt < 20) {
//                            break; // a carrier on a well should never disintegrate, a carrier with max resource gets extra time
//                        }
//                        if (rc.getNumAnchors(Anchor.STANDARD) == 1 && stuckCnt < 40) {
//                            break; // a carrier trying having an anchor gets extra time
//                        }
//                    }
//                    System.out.printf("loc %s disintegrate due to stuck\n", rc.getLocation());
//                    rc.disintegrate();
//                } while (false);
//            }
            if (pathingCnt == 0) {
                //if free of obstacle: try go directly to target
                Direction dir = rc.getLocation().directionTo(location);
                boolean dirCanPass = canPass(rc,dir);
                boolean dirRightCanPass = canPass(rc,dir.rotateRight());
                boolean dirLeftCanPass = canPass(rc,dir.rotateLeft());
                if (dirCanPass || dirRightCanPass || dirLeftCanPass) {
                    if (dirCanPass && canMove(rc,dir)) {
                        fillLoc= tryMove(rc,dir);
                    } else if (dirRightCanPass &&canMove(rc,dir.rotateRight())) {
                        fillLoc=tryMove(rc,dir.rotateRight());
                    } else if (dirLeftCanPass && canMove(rc,dir.rotateLeft())) {
                        fillLoc=tryMove(rc,dir.rotateLeft());
                    }
                } else {
                    //encounters obstacle; run simulation to determine best way to go
                    while (!canPass(rc,dir) && pathingCnt != 8) {
//                        rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(dir), 0, 0, 255);
                        if (!rc.onTheMap(rc.getLocation().add(dir))) {
                            currentTurnDir ^= 1;
                            pathingCnt = 0;
                            disableTurnDirRound = rc.getRoundNum() + 100;
                            return fillLoc;
                        }
                        prv[pathingCnt] = dir;
                        pathingCnt++;
                        if (currentTurnDir == 0) dir = dir.rotateLeft();
                        else dir = dir.rotateRight();
                    }
                    if (pathingCnt == 8) {
                    } else if (canMove(rc,dir)) {
                        fillLoc=tryMove(rc,dir);
                    }
                }
            } else {
                //update stack of past directions, move to next available direction
                if (pathingCnt > 1 && canPass(rc, prv[pathingCnt - 2])) {
                    pathingCnt -= 2;
                }
                while (pathingCnt > 0 && canPass(rc, prv[pathingCnt - 1])) {
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt - 1]), 0, 255, 0);
                    pathingCnt--;
                }
                if (pathingCnt == 0) {
                    Direction dir = rc.getLocation().directionTo(location);
                    if (!canPass(rc, dir)) {
                        prv[pathingCnt++] = dir;
                    }
                }
                int pathingCntCutOff = Math.min(PRV_LENGTH, pathingCnt + 8); // if 8 then all dirs blocked
                while (pathingCnt > 0 && !canPass(rc,currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight())) {
                    prv[pathingCnt] = currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight();
//                    rc.setIndicatorLine(rc.getLocation(), rc.getLocation().add(prv[pathingCnt]), 255, 0, 0);
                    if (!rc.onTheMap(rc.getLocation().add(prv[pathingCnt]))) {
                        currentTurnDir ^= 1;
                        pathingCnt = 0;
                        disableTurnDirRound = rc.getRoundNum() + 100;
                        return fillLoc;
                    }
                    pathingCnt++;
                    if (pathingCnt == pathingCntCutOff) {
                        pathingCnt = 0;
                        return fillLoc;
                    }
                }
                Direction moveDir = pathingCnt == 0? prv[pathingCnt] :
                        (currentTurnDir == 0?prv[pathingCnt - 1].rotateLeft():prv[pathingCnt - 1].rotateRight());
                if (canMove(rc,moveDir)) {
                    tryMove(rc,moveDir);
                } else {
                    // a robot blocking us while we are following wall, wait
                }
            }
        }
        lastPathingTarget = location;
        lastPathingTurn = turnCount;
        return fillLoc;
    }

    private static final int BYTECODE_CUTOFF = 3000;
    static int getTurnDir(RobotController rc, Direction direction, MapLocation target, Random rng) throws GameActionException{
        //int ret = getCenterDir(direction);
        MapLocation now = rc.getLocation();
        int moveLeft = 0;
        int moveRight = 0;

        pathingCnt_ = 0;
        Direction dir = direction;
        while (!canPass(rc, now.add(dir)) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateLeft();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        int byteCodeRem = Clock.getBytecodesLeft();
        if (byteCodeRem < BYTECODE_CUTOFF)
            return rng.nextInt(2);
        //simulate turning left
        while (pathingCnt_ > 0) {
            moveLeft++;
            if (moveLeft > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveLeft = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(rc,now.add(prv_[pathingCnt_ - 1]))) {
                pathingCnt_--;
            }
            if (pathingCnt_ > 1 && canPass(rc,now.add(prv_[pathingCnt_ - 1]))) {
                pathingCnt_-=2;
            }
            while (pathingCnt_ > 0 && !canPass(rc,now.add(prv_[pathingCnt_ - 1].rotateLeft()))) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateLeft();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveLeft = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateLeft();
            now = now.add(moveDir);
        }
        MapLocation leftend = now;
        pathingCnt_ = 0;
        now = rc.getLocation();
        dir = direction;
        //simulate turning right
        while (!canPass(rc,dir) && pathingCnt_ != 8) {
            prv_[pathingCnt_] = dir;
            pathingCnt_++;
            dir = dir.rotateRight();
            if (pathingCnt_ > 8) {
                break;
            }
        }
        now = now.add(dir);

        while (pathingCnt_ > 0) {
            moveRight++;
            if (moveRight > MAX_DEPTH) {
                break;
            }
            if (Clock.getBytecodesLeft() < BYTECODE_CUTOFF) {
                moveRight = -1;
                break;
            }
            while (pathingCnt_ > 0 && canPass(rc,now.add(prv_[pathingCnt_ - 1]))) {
                pathingCnt_--;
            }
            if (pathingCnt_ > 1 && canPass(rc,now.add(prv_[pathingCnt_ - 1]))) {
                pathingCnt_-=2;
            }
            while (pathingCnt_ > 0 && !canPass(rc,now.add(prv_[pathingCnt_ - 1].rotateRight()))) {
                prv_[pathingCnt_] = prv_[pathingCnt_ - 1].rotateRight();
                pathingCnt_++;
                if (pathingCnt_ > 8) {
                    moveRight = -1;
                    break;
                }
            }
            if (pathingCnt_ > 8 || pathingCnt_ == 0) {
                break;
            }
            Direction moveDir = pathingCnt_ == 0? prv_[pathingCnt_] : prv_[pathingCnt_ - 1].rotateRight();
            now = now.add(moveDir);
        }
        MapLocation rightend = now;
        //find best direction
        if (moveLeft == -1 || moveRight == -1) return rng.nextInt(2);
        if (moveLeft + getSteps(leftend, target) <= moveRight + getSteps(rightend, target)) return 0;
        else return 1;

    }
    static int getSteps(MapLocation a, MapLocation b) {
        int xdif = a.x - b.x;
        int ydif = a.y - b.y;
        if (xdif < 0) xdif = -xdif;
        if (ydif < 0) ydif = -ydif;
        if (xdif > ydif) return xdif;
        else return ydif;
    }
}
