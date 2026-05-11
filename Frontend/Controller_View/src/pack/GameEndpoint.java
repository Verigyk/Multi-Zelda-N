package pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@ServerEndpoint(value = "/GameEndpoint/{matchId}/ws", configurator = GameEndpoint.Config.class)
public class GameEndpoint {
    private static final String COOKIE_HEADER_KEY = "cookieHeader";
    private static final String MATCHES_API_BASE = System.getProperty("facade.matches.url",
            "http://localhost:8080/facade/matches");
    private static final Client REST_CLIENT = new ResteasyClientBuilder().build();
    private static final int PLAYER_HALF_SIZE = 25;
    private static final int[][] OBSTACLES = new int[][]{
        new int[]{300, 500, 200, 150},
        new int[]{900, 400, 200, 300}
    };

    private static final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private static final Map<Session, PlayerRef> sessionToPlayer = new ConcurrentHashMap<>();

    public static class Config extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            List<String> cookieHeaders = request.getHeaders().get("cookie");
            if (cookieHeaders == null || cookieHeaders.isEmpty()) {
                cookieHeaders = request.getHeaders().get("Cookie");
            }
            if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                sec.getUserProperties().put(COOKIE_HEADER_KEY, cookieHeaders.get(0));
            }
        }
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("matchId") String matchId) throws IOException {
        GameRoom room = rooms.computeIfAbsent(matchId, id -> new GameRoom());
        PlayerState player = room.addPlayer();
        PlayerRef ref = new PlayerRef(matchId, player.id);

        sessionToPlayer.put(session, ref);
        room.sessions.put(session, player.id);

        session.getBasicRemote().sendText(playersMessage(room.players).toString());
        session.getBasicRemote().sendText(youAreMessage(player.id).toString());
        room.broadcast(playersMessage(Map.of(player.id, player)));
        room.broadcast(roomStateMessage(room));
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        PlayerRef ref = sessionToPlayer.get(session);
        if (ref == null) return;

        GameRoom room = rooms.get(ref.matchId);
        if (room == null) return;

        PlayerState player = room.players.get(ref.playerId);
        if (player == null) return;

        if ("READY".equals(message)) {
            player.ready = true;
            if ("WAITING".equals(room.state) && room.allPlayersReady()) {
                startMatch(ref.matchId, session);
                room.state = "RUNNING";
            }
            room.broadcast(roomStateMessage(room));
            return;
        }

        if (!"RUNNING".equals(room.state)) {
            return;
        }

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
        try {
            leaveMatch(ref.matchId, session);
        } catch (Exception exception) {
            System.err.println("Unable to leave match " + ref.matchId + ": " + exception.getMessage());
        }
        room.broadcast(removePlayersMessage(ref.playerId));
        room.broadcast(roomStateMessage(room));
        MatchesEndpoint.broadcastSnapshotsToOpenSessions();

        if (room.sessions.isEmpty()) {
            rooms.remove(ref.matchId);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String id = session == null ? "unknown" : session.getId();
        System.err.println("Game WebSocket error for session " + id + ": " + throwable.getMessage());
    }

    private void startMatch(String matchId, Session session) {
        doPost("/" + matchId + "/start", session);
        MatchesEndpoint.broadcastSnapshotsToOpenSessions();
    }

    private void leaveMatch(String matchId, Session session) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + "/" + matchId + "/leave")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .post(Entity.entity("", MediaType.APPLICATION_JSON));
        try {
            if (response.getStatus() == 404) {
                return;
            }
            extractBody("/" + matchId + "/leave", response);
        } finally {
            response.close();
        }
    }

    private void doPost(String path, Session session) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + path)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .post(Entity.entity("", MediaType.APPLICATION_JSON));
        extractBody(path, response);
    }

    private String extractBody(String path, Response response) {
        try {
            String body = response.hasEntity() ? response.readEntity(String.class) : "";
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                return body;
            }
            throw new RuntimeException("HTTP " + response.getStatus() + " sur " + path);
        } finally {
            response.close();
        }
    }

    private String getCookieHeader(Session session) {
        Object cookieHeader = session.getUserProperties().get(COOKIE_HEADER_KEY);
        if (cookieHeader == null) {
            throw new RuntimeException("Token absent: connecte-toi avant de lancer la partie");
        }
        return cookieHeader.toString();
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

    private JSONObject youAreMessage(String playerId) {
        JSONObject json = new JSONObject();
        json.put("type", "YouAre");
        json.put("playerId", playerId);
        return json;
    }

    private JSONObject roomStateMessage(GameRoom room) {
        JSONObject ready = new JSONObject();
        for (PlayerState player : room.players.values()) {
            ready.put(player.id, player.ready);
        }

        JSONObject json = new JSONObject();
        json.put("type", "RoomState");
        json.put("state", room.state);
        json.put("ready", ready);
        json.put("playersCount", room.players.size());
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
        private String state = "WAITING";

        private PlayerState addPlayer() {
            int id = nextPlayerId.getAndIncrement();
            PlayerState player = new PlayerState("p" + id, 50 + id * 100, 50);
            players.put(player.id, player);
            return player;
        }

        private boolean allPlayersReady() {
            return !players.isEmpty() && players.values().stream().allMatch(player -> player.ready);
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
        private boolean ready;

        private PlayerState(String id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.ready = false;
        }
    }
}
