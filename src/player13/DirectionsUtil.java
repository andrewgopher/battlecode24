package player13;

import battlecode.common.Direction;
import java.util.Random;



public strictfp class DirectionsUtil {
    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    public static Direction randomDirection(Random rng) {
        return directions[rng.nextInt(directions.length)];
    }
}
