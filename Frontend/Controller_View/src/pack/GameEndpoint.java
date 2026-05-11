package pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/GameEndpoint/{matchId}/ws")
public class GameEndpoint {
    private static final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private static final Map<Session, PlayerRef> sessionToPlayer = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("matchId") String matchId) throws IOException {
        GameRoom room = rooms.computeIfAbsent(matchId, id -> new GameRoom());
        PlayerState player = room.addPlayer();
        PlayerRef ref = new PlayerRef(matchId, player.id);

        sessionToPlayer.put(session, ref);
        room.sessions.put(session, player.id);

        session.getBasicRemote().sendText(playersMessage(room.players).toString());
        room.broadcast(playersMessage(Map.of(player.id, player)));
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        PlayerRef ref = sessionToPlayer.get(session);
        if (ref == null) return;

        GameRoom room = rooms.get(ref.matchId);
        if (room == null) return;

        PlayerState player = room.players.get(ref.playerId);
        if (player == null) return;

        if (move(player, message)) {
            room.broadcast(playersMessage(Map.of(player.id, player)));
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        PlayerRef ref = sessionToPlayer.remove(session);
        if (ref == null) return;

        GameRoom room = rooms.get(ref.matchId);
        if (room == null) return;

        room.sessions.remove(session);
        room.players.remove(ref.playerId);
        room.broadcast(removePlayersMessage(ref.playerId));

        if (room.sessions.isEmpty()) {
            rooms.remove(ref.matchId);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String id = session == null ? "unknown" : session.getId();
        System.err.println("Game WebSocket error for session " + id + ": " + throwable.getMessage());
    }

    private boolean move(PlayerState player, String direction) {
        switch (direction) {
            case "HAUT":
                player.y -= PlayerState.WALK_STEP;
                return true;
            case "BAS":
                player.y += PlayerState.WALK_STEP;
                return true;
            case "GAUCHE":
                player.x -= PlayerState.WALK_STEP;
                return true;
            case "DROITE":
                player.x += PlayerState.WALK_STEP;
                return true;
            default:
                return false;
        }
    }

    private JSONObject playersMessage(Map<String, PlayerState> players) {
        JSONObject data = new JSONObject();
        for (PlayerState player : players.values()) {
            data.put(player.id, new JSONArray(new int[]{player.x, player.y}));
        }

        JSONObject json = new JSONObject();
        json.put("type", "Players");
        json.put("data", data);
        return json;
    }

    private JSONObject removePlayersMessage(String playerId) {
        ArrayList<String> ids = new ArrayList<>();
        ids.add(playerId);

        JSONObject json = new JSONObject();
        json.put("type", "RemovePlayers");
        json.put("data", new JSONArray(ids));
        return json;
    }

    private static class GameRoom {
        private final Map<Session, String> sessions = new ConcurrentHashMap<>();
        private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
        private final AtomicInteger nextPlayerId = new AtomicInteger(0);

        private PlayerState addPlayer() {
            int id = nextPlayerId.getAndIncrement();
            PlayerState player = new PlayerState("p" + id, 50 + id * 100, 50);
            players.put(player.id, player);
            return player;
        }

        private void broadcast(JSONObject json) throws IOException {
            for (Session session : sessions.keySet()) {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(json.toString());
                }
            }
        }
    }

    private static class PlayerRef {
        private final String matchId;
        private final String playerId;

        private PlayerRef(String matchId, String playerId) {
            this.matchId = matchId;
            this.playerId = playerId;
        }
    }

    private static class PlayerState {
        private static final int WALK_STEP = 5;

        private final String id;
        private int x;
        private int y;

        private PlayerState(String id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }
}
