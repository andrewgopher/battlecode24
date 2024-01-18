package player8;

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
}
