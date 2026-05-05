package pack.backendObjects;

import java.util.Collection;

public class ModelMatch {
    Long id;
    
    Collection<Player> players;

    int coordinate_x = 50;

    public static final int spawn_step = 100;

    public ModelMatch() {

    }


    public Long getId() {
        return id;
    }

    public int getCoordinate_x() {
        return coordinate_x;
    }
}
