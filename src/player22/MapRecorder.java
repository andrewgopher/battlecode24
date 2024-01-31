package player22;

import battlecode.common.*;

public class MapRecorder {
    char[][] tiles = new char[60][60];
    int seenBit = 0;
    int wallBit = 1;

    int spawnBit = 2;//two bits

    public void update(RobotController rc) throws GameActionException {
        int bcStart =0;
        MapInfo[] mapInfos = rc.senseNearbyMapInfos();
        for (MapInfo mapInfo : mapInfos) {
            MapLocation loc = mapInfo.getMapLocation();
            setSeen(loc);
            if (mapInfo.isWall()) {
                setWall(loc);
            }
            if (mapInfo.isSpawnZone()) {
                setSpawn(loc, mapInfo.getSpawnZoneTeamObject());
            }
            eliminateSymmetries(rc, loc);
            if (Clock.getBytecodeNum() - bcStart > 2000) {
                break;
            }
        }
    }

    public void eliminateSymmetries(RobotController rc, MapLocation loc)throws GameActionException {
        char opp = toggleSpawn(tiles[loc.x][loc.y]);

        //rotational
        MapLocation rotLoc = new MapLocation(rc.getMapWidth()-loc.x-1, rc.getMapHeight()-loc.y-1);
        if (getSeen(rotLoc)) {
            if (tiles[rotLoc.x][rotLoc.y] != opp) {
                Communicator.eliminateSymmetry(rc,Symmetry.ROTATIONAL);
            }
        }

        //horiz
        MapLocation horizLoc = new MapLocation(loc.x, rc.getMapHeight()-loc.y-1);
        if (getSeen(horizLoc)) {
            if (tiles[horizLoc.x][horizLoc.y] != opp) {
                Communicator.eliminateSymmetry(rc,Symmetry.HORIZONTAL);
            }
        }

        //vert
        MapLocation vertLoc = new MapLocation(rc.getMapWidth()-loc.x-1, loc.y);
        if (getSeen(vertLoc)) {
            if (tiles[vertLoc.x][vertLoc.y] != opp) {
                Communicator.eliminateSymmetry(rc,Symmetry.VERTICAL);
            }
        }
    }

    public void setSeen(MapLocation loc) {
        tiles[loc.x][loc.y] |= (char) (1<<seenBit);
    }

    public boolean getSeen(MapLocation loc) {
        return (tiles[loc.x][loc.y] & (char) (1<<seenBit))>0;
    }

    public void setWall(MapLocation loc) {
        tiles[loc.x][loc.y] |= (char) (1<<wallBit);
    }

    public boolean getWall(MapLocation loc) {
        return (tiles[loc.x][loc.y] & (char) (1<<wallBit))>0;
    }

    public void setSpawn(MapLocation loc, Team team) {
        if (team == Team.A) {
            tiles[loc.x][loc.y] |= (char) (1<<spawnBit);
        } else {
            tiles[loc.x][loc.y] |= (char) (1<<(spawnBit+1));
        }
    }

    public char toggleSpawn(char x) {
        if ((x & (1<<spawnBit))>0) {//A
            x -= (char) (1<<spawnBit);
            x |= (char) (1<<(spawnBit+1));
        } else if ((x & (1<<spawnBit+1))>0) {//B
            x -= (char) (1<<(spawnBit+1));
            x |= (char) (1<<(spawnBit));
        }
        return x;
    }
}
