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
    private static final int PLAYER_HALF_SIZE = 25;
    private static final int[][] OBSTACLES = new int[][]{
        new int[]{300, 500, 200, 150},
        new int[]{900, 400, 200, 300}
    };

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

        if (move(room, player, message)) {
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

    private boolean move(GameRoom room, PlayerState player, String direction) {
        int nextX = player.x;
        int nextY = player.y;

        switch (direction) {
            case "HAUT":
                nextY -= PlayerState.WALK_STEP;
                break;
            case "BAS":
                nextY += PlayerState.WALK_STEP;
                break;
            case "GAUCHE":
                nextX -= PlayerState.WALK_STEP;
                break;
            case "DROITE":
                nextX += PlayerState.WALK_STEP;
                break;
            default:
                return false;
        }

        if (!canMove(room, player, nextX, nextY)) {
            return false;
        }

        player.x = nextX;
        player.y = nextY;
        return true;
    }

    private boolean canMove(GameRoom room, PlayerState movedPlayer, int nextX, int nextY) {
        for (PlayerState player : room.players.values()) {
            if (!player.id.equals(movedPlayer.id) && collidesPlayerWithRect(nextX, nextY, player.x + PLAYER_HALF_SIZE, player.y + PLAYER_HALF_SIZE, PLAYER_HALF_SIZE, PLAYER_HALF_SIZE)) {
                return false;
            }
        }

        for (int[] obstacle : OBSTACLES) {
            if (collidesPlayerWithRect(nextX, nextY, obstacle[0], obstacle[1], obstacle[2], obstacle[3])) {
                return false;
            }
        }

        return true;
    }

    private boolean collidesPlayerWithRect(int playerLeft, int playerTop, int rectCenterX, int rectCenterY, int rectHalfWidth, int rectHalfHeight) {
        int playerCenterX = playerLeft + PLAYER_HALF_SIZE;
        int playerCenterY = playerTop + PLAYER_HALF_SIZE;

        return Math.abs(rectCenterX - playerCenterX) < rectHalfWidth + PLAYER_HALF_SIZE
            && Math.abs(rectCenterY - playerCenterY) < rectHalfHeight + PLAYER_HALF_SIZE;
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
