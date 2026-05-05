package pack.backendObjects;

import java.util.Map;

public class SocketConvention {
    boolean send;
    Map<String, int[]> players;

    public SocketConvention() {

    }

    public boolean getSend() {
        return this.send;
    }

    public Map<String, int[]> getPlayers() {
        return players;
    }
}
