package pack.backendObjects;

public class Player {

    String name;

    int x;
    int y;

    public static final int walk_step = 5;
    public static final int cote = 25;

    public Player() {

    }

    public String getName() {
        return this.name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
