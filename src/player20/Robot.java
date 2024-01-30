package player20;

import battlecode.common.*;
import player20.fast.FastIntLocMap;

import java.util.Random;

public class Robot {
    static MapLocation fillLoc;
    static String indicatorString;
    static String spawnIndicatorString;
    static Random rng;
    static MapLocation[] sharedAllyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedEnemyFlagInfo =  new MapLocation[3];
    static MapLocation[] sharedAllyFlagCarrierInfo = new MapLocation[3];
    static MapLocation[] sharedEnemyFlagCarrierInfo = new MapLocation[3];
    static MapLocation[] sharedAllySpawnInfo = new MapLocation[3];
    static boolean[] symmetries= {true, true, true}; //rot, hor, vert
    static int[] sharedAllySpawnEnemiesInfo = new int[3];

    static int[] sharedAllySpawnTurnInfo = new int[3];

    static MapLocation spawnPos;

    static boolean isDefender = false;

    static GlobalUpgrade[] globalUpgrades = {GlobalUpgrade.ACTION, GlobalUpgrade.HEALING, GlobalUpgrade.CAPTURING};

    static FastIntLocMap lastSeenPrevEnemyLoc = new FastIntLocMap(); //from previous turn only!!

    MapRecorder mapRecorder = new MapRecorder();


    public void indicateSharedInfo(RobotController rc) throws GameActionException {
        for (MapLocation loc : sharedAllyFlagInfo) {
            if (loc != null) {
//                rc.setIndicatorDot(loc, 0, 100, 0);
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
//                indicatorString += loc + " " + Communicator.interpretNumber(rc, Communicator.enemyFlagCarriersTurn+11*i,11)+ " " + Communicator.interpretNumber(rc,Communicator.enemyFlagCarriersEscort+6*i,6)+",";
            }
            i++;
        }
//        indicatorString += symmetries[0] + " " + symmetries[1] + " " + symmetries[2] + ",";
        i = 0;
        for (MapLocation loc :sharedAllySpawnInfo) {
            if (loc != null) {
                rc.setIndicatorDot(loc,0,255,0);
//                indicatorString += loc + " " + sharedAllySpawnTurnInfo[i]+ " " + sharedAllySpawnEnemiesInfo[i]+ " " + Communicator.interpretNumber(rc,Communicator.allySpawnsAllies+12*i,12)+ ",";
                indicatorString+=Communicator.getEscortCnt(rc,i)+",";
            }
            i++;
        }
    }

    public void processSharedArray(RobotController rc) throws  GameActionException {
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo,
                sharedEnemyFlagCarrierInfo,symmetries,sharedAllySpawnInfo,sharedAllySpawnEnemiesInfo,
                sharedAllySpawnTurnInfo);
    }

    public void initTurn(RobotController rc) throws GameActionException {
        indicatorString  = "";
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo,
                sharedEnemyFlagCarrierInfo,symmetries,sharedAllySpawnInfo,sharedAllySpawnEnemiesInfo,
                sharedAllySpawnTurnInfo);
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
            for (int i = 0; i < 3; i++) {
                Communicator.writeNumber(rc,Communicator.interceptCnt+i*6,0,6);
            }
            for (int i = 0; i < 3; i ++) {
                Communicator.writeNumber(rc, Communicator.escortCnt+6*i,0,6);
            }

            for (int i = 0; i < 3; i ++) {
                Communicator.writeNumber(rc,Communicator.allySpawnsEnemies+12*i,Communicator.interpretNumber(rc,Communicator.newAllySpawnsEnemies+12*i,12),12);
                Communicator.writeNumber(rc,Communicator.allySpawnsAllies+12*i,Communicator.interpretNumber(rc,Communicator.newAllySpawnsAllies+12*i,12),12);
                Communicator.writeNumber(rc,Communicator.newAllySpawnsEnemies+12*i,0,12);
                Communicator.writeNumber(rc,Communicator.newAllySpawnsAllies+12*i,0,12);
            }
        }
        int isCenterCnt = 0;
        for (Direction dir :Direction.cardinalDirections()) {
            if (rc.canSenseLocation(rc.getLocation().add(dir)) && rc.senseMapInfo(rc.getLocation().add(dir)).isSpawnZone() && rc.senseMapInfo(rc.getLocation().add(dir)).getSpawnZoneTeamObject()==rc.getTeam()) {
                isCenterCnt++;
            }
        }
        if (isCenterCnt==4) {
            Communicator.addAllySpawn(rc, rc.getLocation());
        }


        int minDistToAllySpawn = Util.BigNum;
        int minDistAllySpawn = -1;
        for (int i = 0; i < 3; i ++) {
            if (sharedAllySpawnInfo[i] != null && sharedAllySpawnInfo[i].distanceSquaredTo(rc.getLocation()) < minDistToAllySpawn) {
                minDistAllySpawn = i;
                minDistToAllySpawn = sharedAllySpawnInfo[i].distanceSquaredTo(rc.getLocation());
            }
        }

        if (minDistToAllySpawn <= Math.min(rc.getMapHeight(),rc.getMapWidth())*Math.min(rc.getMapHeight(),rc.getMapWidth())/9) {
            Communicator.updateAllySpawn(rc,minDistAllySpawn);
        }


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
        if (spawnIndicatorString.isEmpty()) {
            rc.setIndicatorString(indicatorString);
        } else {
            rc.setIndicatorString(spawnIndicatorString);
        }
    }
    public void spawn(RobotController rc, MapLocation mySpawnLoc) throws GameActionException {
        rc.spawn(mySpawnLoc);
        Communicator.processSharedArray(rc, sharedAllyFlagInfo, sharedEnemyFlagInfo, sharedAllyFlagCarrierInfo,
                sharedEnemyFlagCarrierInfo,symmetries,sharedAllySpawnInfo,sharedAllySpawnEnemiesInfo,
                sharedAllySpawnTurnInfo);
        indicateSharedInfo(rc);
        Communicator.updateEnemyFlagLocations(rc);
        rng = new Random(rc.getID());
        if (rc.getRoundNum() == 1 && rc.readSharedArray(63) == 0) {
            Communicator.wipeArray(rc, (1 << 16) - 1);
            Communicator.writeNumber(rc, Communicator.enemyFlagCapturedStart, 0, 3);
            Communicator.writeNumber(rc, Communicator.allySpawnsEnemies,0,6);
            Communicator.writeNumber(rc, Communicator.allySpawnsEnemies+6,0,6);
            Communicator.writeNumber(rc, Communicator.allySpawnsEnemies+6*2,0,6);
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
