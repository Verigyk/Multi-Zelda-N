package zelda.facade.matches;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ModeleMatch {
    private Match match;
    private HashMap<String, int[]> players_coordinates;

    public ModeleMatch(Match match, Collection<String> players_id) {
        this.match = match;
        this.players_coordinates = new HashMap<String, int[]>();

        this.initialize_coordinates(players_id);
    }

    public void translate(String player_id, int x, int y) {
        this.players_coordinates.get(player_id)[0] += x;
        this.players_coordinates.get(player_id)[1] += y;
    } 

    private void initialize_coordinates(Collection<String> players_id) {
        int[] coordinates = {50, 50};

        for (String player_id : players_id) {
            this.players_coordinates.put(player_id, coordinates);

            //Modification du point d'apparition initial
            coordinates[0] += 100;
        }
    }
}
