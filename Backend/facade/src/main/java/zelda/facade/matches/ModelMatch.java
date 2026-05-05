package zelda.facade.matches;

import java.util.Collection;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import zelda.facade.players.Player;

@Entity
@EnableAutoConfiguration
public class ModelMatch {
    @Id
    Long id;
    
    @OneToMany
    Collection<Player> players;

    int coordinate_x = 50;

    @Transient
    public static final int spawn_step = 100;

    public ModelMatch() {

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCoordinatesPlayers(Collection<Player> players) {
        this.players = players;
    }

    public int getCoordinate_x() {
        return coordinate_x;
    }

    public void setCoordinate_x(int coordinate_x) {
        this.coordinate_x = coordinate_x;
    }
}
