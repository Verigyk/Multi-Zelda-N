package zelda.facade.RequestShape;

import java.util.Map;

public class SocketConvention {
    boolean send;
    Map<String, int[]> players;

    public SocketConvention() {

    }

    public SocketConvention(boolean canBeSent, Map<String, int[]> players) {
        this.send = canBeSent;
        this.players = players;
    }

    public SocketConvention(boolean canBeSent) {
        this.send = canBeSent;
    }

    public boolean getSend() {
        return this.send;
    }

    public Map<String, int[]> getPlayers() {
        return players;
    }

    public void setPlayers(Map<String, int[]> players) {
        this.players = players;
    }

    public void setSend(boolean canBeSent) {
        this.send = canBeSent;
    }
}
