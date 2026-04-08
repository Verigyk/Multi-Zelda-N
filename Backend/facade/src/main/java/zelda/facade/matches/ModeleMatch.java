package zelda.facade.matches;

import java.util.ArrayList;
import java.util.Vector;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public class ModeleMatch {
    private Match match;
    private String player_id;
    private int[] coordinates = {50, 50};

    public ModeleMatch(Match match, String player_id) {
        this.match = match;
        this.player_id = player_id;
    }
    
    public int[] getPoint() {
        return this.coordinates;
    }

    public void translate(int x, int y) {
        this.coordinates[0] += x;
        this.coordinates[1] += y;
    } 
}
