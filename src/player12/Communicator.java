package player12;

import battlecode.common.*;

public strictfp class Communicator {
    //fixed location counts
    //shared array bit locations of various things
    static final int roundNumStart= 0;
    static final int allyFlagLocationsStart = roundNumStart+11;
    static final int enemyFlagLocationsStart = allyFlagLocationsStart+3*12;
    static final int enemyFlagIDsStart = enemyFlagLocationsStart+3*12;
    static final int enemyFlagCapturedStart = enemyFlagIDsStart+3*14;
    static final int allyFlagCarriersStart = enemyFlagCapturedStart+3;

    static final int isAllyFlagCarrierAliveStart = allyFlagCarriersStart+3*12;

    static int lastProcessTurn = -1;




    //wipes array with value x
    public static void wipeArray(RobotController rc, int x) throws GameActionException {
        for (int i = 0; i < 64; i ++) {
            rc.writeSharedArray(i, x);
        }
    }

    // Writes bit at absolute index in entire array (no regard for the 16-bit width)
    public static void writeBit(RobotController rc, int index, boolean bit) throws GameActionException {
        int block = rc.readSharedArray(index/16);
        int bitIndex = index % 16;
        if (bit) {
            block |= (1<<(15-bitIndex));

        } else {
            block &= ~(1<<(15-bitIndex));
        }
        rc.writeSharedArray(index/16, block);
    }

    public static void writeNumber(RobotController rc, int index, int num, int bits) throws GameActionException {
        int block = index/16;
        int blockInd = index%16;
        int currNum = rc.readSharedArray(block);
        if (blockInd+bits <= 16) {
            int mask = ((1<<16)-1)-(((1<<(16-blockInd))-1)-((1<<(16-(blockInd+bits)))-1));
            currNum &= mask;
            currNum |= num << (16-(blockInd+bits));
            rc.writeSharedArray(block, currNum);
        } else {
            int mask = ((1<<16)-1)-((1<<(16-blockInd))-1);
            currNum &= mask;
            currNum |= num>>(bits-(16-blockInd));
            rc.writeSharedArray(block, currNum);

            mask = ((1<<(16-(bits-(16-blockInd))))-1);
            currNum = rc.readSharedArray(block+1);
            currNum &= mask;
            currNum |= (num<<(16-(bits-(16-blockInd))))&((1<<16)-1);
            rc.writeSharedArray(block+1,currNum);
        }
    }

    public static void writeLocation(RobotController rc, int index, MapLocation loc) throws GameActionException {
        writeNumber(rc, index, loc.x,6);
        writeNumber(rc, index+6, loc.y,6);
    }

    // Reads bit at absolute index in entire array (no regard for the 16-bit width)
    public static boolean readBit(RobotController rc, int index) throws GameActionException {
        return (rc.readSharedArray(index/16) & (1<<(15-(index%16)))) > 0;
    }

    // Interprets the bits from index to index+bits-1 as an integer ; assumes bits<=16
    public static int interpretNumber(RobotController rc, int index, int bits) throws GameActionException {
        int block=index/16;
        int blockInd = index%16;
        if (blockInd + bits <= 16) {
            return (rc.readSharedArray(block)>>(16-blockInd-bits))&((1<<bits)-1);
        } else {
            int second = bits-(16-blockInd);
            return ((rc.readSharedArray(block)&((1<<(16-blockInd))-1))<<second)+((rc.readSharedArray(block+1))>>(16-second));
        }
    }

    // Interprets the bits from index to index+11 as a MapLocation
    public static MapLocation interpretLocation(RobotController rc, int index) throws GameActionException {
        int x = interpretNumber(rc, index, 6);
        int y = interpretNumber(rc, index+6, 6);
        if (x >= 60 || y >=60) {
            return null;
        }
        return new MapLocation(x,y);
    }

    //process information from shared array
    public static void processSharedArray(RobotController rc, MapLocation[] sharedAllyFlagInfo, MapLocation[] sharedEnemyFlagInfo, MapLocation[] sharedAllyFlagCarrierInfo) throws GameActionException {
        if (rc.getRoundNum() == lastProcessTurn) {
            return;
        }
        lastProcessTurn = rc.getRoundNum();
        for (int i = 0; i < 3; i ++) {
            sharedAllyFlagInfo[i] = interpretLocation(rc, allyFlagLocationsStart +i*12);
        }
        for (int i = 0; i < 3; i ++) {
            sharedEnemyFlagInfo[i] = interpretLocation(rc, enemyFlagLocationsStart +i*12);
        }
        for (int i = 0; i < 3; i ++) {
            sharedAllyFlagCarrierInfo[i] = interpretLocation(rc, allyFlagCarriersStart +i*12);
        }
    }

    //update shared array with my own sensed information
    public static void updateEnemyFlagLocations(RobotController rc) throws GameActionException {
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1);
        for (FlagInfo flag : nearbyFlags) { //TOOD: only add untouched
            if (flag.getTeam() != rc.getTeam() && rc.getRoundNum() >= GameConstants.SETUP_ROUNDS && !flag.isPickedUp()) {
                boolean isNew = true;
                int flagID=flag.getID();
                for (int i = 0; i < 3; i ++) {
                    if (interpretNumber(rc, enemyFlagIDsStart+14*i, 14) == flagID) {
                        isNew=false;
                        break;
                    }
                }
                if (isNew) {
                    writeEnemyFlagLocation(rc, flag.getLocation(), flagID);
                }
            }
        }
    }




    //writes the location at the first empty 12 bits
    public static void writeLocationAtFirstEmptyIfUnique(RobotController rc, int startIndex, int endIndex, MapLocation loc) throws GameActionException {
        int currX, currY;
        while (startIndex < endIndex) {
            currX = interpretNumber(rc, startIndex, 6);
            currY = interpretNumber(rc, startIndex+6, 6);
            if (currX == 63 && currY == 63) {
                writeLocation(rc, startIndex, loc);
                return;
            }

            if (currX == loc.x && currY == loc.y) {
                break;
            }
            startIndex += 12;
        }
    }
    public static int writeLocationAtFirstEmpty(RobotController rc, int startIndex, int endIndex, MapLocation loc) throws GameActionException {
        int currX, currY;
        int i = 0;
        while (startIndex < endIndex) {
            currX = interpretNumber(rc, startIndex, 6);
            currY = interpretNumber(rc, startIndex+6, 6);
            if (currX == 63 && currY == 63) {
                writeLocation(rc, startIndex, loc);
                return i;
            }
            startIndex += 12;
            i++;
        }
        return -1;
    }

    public static void eraseFirstLocation(RobotController rc, int startIndex, int endIndex, MapLocation loc) throws GameActionException {
        while (startIndex < endIndex) {
            if (interpretNumber(rc, startIndex, 6) == loc.x && interpretNumber(rc, startIndex + 6, 6) == loc.y) {
                writeNumber(rc, startIndex, (1<<12)-1, 12);
                return;
            }
            startIndex += 12;
        }
    }

    public static void writeAllyFlagLocation(RobotController rc, MapLocation loc) throws GameActionException {
        writeLocationAtFirstEmptyIfUnique(rc, allyFlagLocationsStart, allyFlagLocationsStart+3*12, loc);
    }

    public static void writeEnemyFlagLocation(RobotController rc, MapLocation loc, int id) throws GameActionException {
        int i = writeLocationAtFirstEmpty(rc, enemyFlagLocationsStart, enemyFlagLocationsStart+3*12, loc);
        if (i != -1) {
            writeNumber(rc, enemyFlagIDsStart + 14 * i, id, 14);
        }
    }

    public static void writeAllyFlagCarrier(RobotController rc, MapLocation loc) throws GameActionException {
        writeLocationAtFirstEmpty(rc, allyFlagCarriersStart, allyFlagCarriersStart+3*12, loc);
    }
}
