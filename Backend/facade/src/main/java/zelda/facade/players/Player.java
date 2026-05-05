package zelda.facade.players;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@Entity
@EnableAutoConfiguration
public class Player {

    //@OneToOne(mappedBy = "player", optional = false)
    //Account associated_account;

    @Id
    String name;

    int x;
    int y;

    int initial_x;
    int initial_y;

    String orientation;

    @Transient
    public static final int walk_step = 5;
    @Transient
    public static final int demicote = 25;

    public Player() {

    }

    public Player(String name, int x, int y) {
        this.name = name;

        this.x = x;
        this.y = y;

        this.initial_x = x;
        this.initial_y = y;

        this.orientation = "BAS";
    }

    public String getName() {
        return this.name;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public String getOrientation() {
        return this.orientation;
    }

    public void translateX(int x) {
        this.x += x;
    }
    
    public void translateY(int y) {
        this.y += y;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public void reset() {
        this.x = initial_x;
        this.y = initial_y;
    }
}
