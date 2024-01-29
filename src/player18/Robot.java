package player18;

import battlecode.common.*;
import player18.fast.FastIntLocMap;

import java.util.Random;

public class Robot {
    static MapLocation fillLoc;
    static String indicatorString;
    static Random rng;
    static MapLocation[] sharedAllyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedEnemyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedAllyFlagCarrierInfo = new MapLocation[3];
    static MapLocation[] sharedEnemyFlagCarrierInfo = new MapLocation[3];
    static boolean[] symmetries= {true, true, true}; //rot, hor, vert

    static MapLocation spawnPos;

    static boolean isDefender = false;

    static GlobalUpgrade[] globalUpgrades = {GlobalUpgrade.ACTION, GlobalUpgrade.HEALING, GlobalUpgrade.CAPTURING};

    static FastIntLocMap lastSeenPrevEnemyLoc = new FastIntLocMap(); //from previous turn only!!

    MapRecorder mapRecorder = new MapRecorder();


    public void indicateSharedInfo(RobotController rc) throws GameActionException {
        for (MapLocation loc : sharedAllyFlagInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 0, 100, 0);
            }
        }
        for (MapLocation loc : sharedEnemyFlagInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 100,0, 0);
            }
        }
        for (MapLocation loc : sharedAllyFlagCarrierInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 0, 0,255);
            }
        }
        int i=0;
        for (MapLocation loc : sharedEnemyFlagCarrierInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc, 0, 255,255);
                indicatorString += loc + " " + Communicator.interpretNumber(rc, Communicator.enemyFlagCarriersTurn+11*i,11)+",";
            }
            i++;
        }
        indicatorString += symmetries[0] + " " + symmetries[1] + " " + symmetries[2] + ",";
    }
    public void initTurn(RobotController rc) throws GameActionException {
        indicatorString  = "";
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo,sharedEnemyFlagCarrierInfo,symmetries);
        indicateSharedInfo(rc);
        Communicator.updateEnemyFlagLocations(rc);
        Communicator.updateEnemyFlagCarriers(rc);
        boolean isFirst=false;

        if (rc.getRoundNum() != Communicator.interpretNumber(rc, Communicator.roundNumStart,11)) {
            Communicator.writeNumber(rc, Communicator.roundNumStart, rc.getRoundNum(),11);
            isFirst=true;
        }

        if (isFirst) {
            for (int i = 0; i < 3; i ++) {
                if (!Communicator.readBit(rc, Communicator.isAllyFlagCarrierAliveStart+i)) {
                    Communicator.writeLocation(rc, Communicator.allyFlagCarriersStart+i*12, new MapLocation(63,63));
                }
            }
            Communicator.writeNumber(rc, Communicator.isAllyFlagCarrierAliveStart, 0, 3);
        }


        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos();

        if (rc.canBuyGlobal(globalUpgrades[0])) {
            rc.buyGlobal(globalUpgrades[0]);
        }
        if (rc.canBuyGlobal(globalUpgrades[1])) {
            rc.buyGlobal(globalUpgrades[1]);
        }
        if (rc.canBuyGlobal(globalUpgrades[2])) {
            rc.buyGlobal(globalUpgrades[2]);
        }
        mapRecorder.update(rc);
    }
    public static void endTurn(RobotController rc) throws  GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent()); //TODO: remove redundancy
        for (RobotInfo nearbyEnemy : nearbyEnemies) {
            lastSeenPrevEnemyLoc.addReplace(nearbyEnemy.getID(), nearbyEnemy.getLocation());
        }
        rc.setIndicatorString(indicatorString);
    }
    public void spawn(RobotController rc, MapLocation mySpawnLoc) throws GameActionException {
        rc.spawn(mySpawnLoc);
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo,sharedEnemyFlagCarrierInfo,symmetries);
        indicateSharedInfo(rc);
        Communicator.updateEnemyFlagLocations(rc);
        rng = new Random(rc.getID());
        if (rc.getRoundNum() == 1 && rc.readSharedArray(63) == 0) {
            Communicator.wipeArray(rc, (1 << 16) - 1);
            Communicator.writeNumber(rc, Communicator.enemyFlagCapturedStart, 0, 3);
        }
        spawnPos = rc.getLocation();
    }

    public Symmetry getSymmetry() {
        if (symmetries[0]) {
            return Symmetry.ROTATIONAL;
        } else if (symmetries[1]) {
            return Symmetry.HORIZONTAL;
        } else {
            return Symmetry.VERTICAL;
        }
    }
}
