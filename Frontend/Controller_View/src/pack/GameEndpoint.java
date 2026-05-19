package pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    private static final String FACADE_API_BASE = System.getProperty("facade.api.url",
            "http://localhost:8080/facade");
    private static final String MATCHES_API_BASE = System.getProperty("facade.matches.url",
            "http://localhost:8080/facade/matches");
    private static final Client REST_CLIENT = new ResteasyClientBuilder().build();
    private static final ScheduledExecutorService TIMER_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final int MATCH_DURATION_SECONDS = 180;
    private static final int PLAYER_SIZE = 50;
    private static final int PLAYER_HALF_SIZE = 25;
    private static final int GEM_SIZE = 22;
    private static final int GEM_HALF_SIZE = 11;
    private static final int BOMB_SIZE = 22;
    private static final int BOMB_HALF_SIZE = 11;
    private static final int BOMB_SPAWN_COOLDOWN_MS = 7000;
    private static final int PROJECTILE_SPEED = 14;
    private static final int PROJECTILE_MAX_AGE_MS = 1800;
    private static final int PLAYER_DEAD_DURATION_MS = 5000;
    private static final int RECONNECT_GRACE_SECONDS = 5;
    private static final int GEMS_DROPPED_ON_HIT = 5;
    private static final int[][] BOMB_SPAWN_POINTS = new int[][]{
        new int[]{150, 140},
        new int[]{570, 120},
        new int[]{610, 690},
        new int[]{1120, 620}
    };
    private static final String[] PLAYER_COLORS = new String[]{
        "#ef4444", "#3b82f6", "#22c55e", "#f59e0b", "#a855f7", "#06b6d4", "#f97316", "#ec4899"
    };
    private static final GameMap CLASSIC_MAP = new GameMap(
        "classic",
        1200,
        760,
        8,
        new int[][]{
            new int[]{300, 500, 200, 150},
            new int[]{900, 400, 200, 300}
        },
        new int[][]{
            new int[]{50, 50},
            new int[]{150, 50},
            new int[]{250, 50},
            new int[]{350, 50}
        }
    );

    private static final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private static final Map<Session, PlayerRef> sessionToPlayer = new ConcurrentHashMap<>();

    public static boolean hasActivePlayerSession(String matchId, String pseudo) {
        if (matchId == null || pseudo == null) {
            return false;
        }
        GameRoom room = rooms.get(matchId);
        if (room == null) {
            return false;
        }
        for (Map.Entry<Session, PlayerRef> entry : sessionToPlayer.entrySet()) {
            PlayerRef ref = entry.getValue();
            if (!matchId.equals(ref.matchId)) {
                continue;
            }
            PlayerState player = room.players.get(ref.playerId);
            if (player != null && pseudo.equals(player.pseudo)) {
                return true;
            }
        }
        return false;
    }

    public static void broadcastMatchCancelled(String matchId) {
        GameRoom room = rooms.get(matchId);
        if (room == null) {
            return;
        }
        try {
            room.cancelForCancellation();
        } catch (IOException exception) {
            System.err.println("Unable to broadcast canceled match for " + matchId + ": " + exception.getMessage());
        }
    }

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
        GameRoom room = rooms.computeIfAbsent(matchId, id -> new GameRoom(loadMapForMatch(id, session)));
        String pseudo = getCurrentPseudo(session);
        
        // Check if this player is already in the game
        PlayerState player = room.players.values().stream()
                .filter(p -> pseudo.equals(p.pseudo))
                .findFirst()
                .orElse(null);
        
        if (player == null) {
            // Player not currently in game, check if reconnecting
            player = room.findDisconnectedPlayer(pseudo);
            if (player == null) {
                // New player
                player = room.addPlayer(pseudo);
            } else {
                // Reconnecting after disconnect
                room.reconnectPlayer(session, player);
            }
        }
        
        room.sessions.put(session, player.id);
        PlayerRef ref = new PlayerRef(matchId, player.id);

        sessionToPlayer.put(session, ref);

        session.getBasicRemote().sendText(mapMessage(room.map).toString());
        session.getBasicRemote().sendText(playersMessage(room.players).toString());
        session.getBasicRemote().sendText(youAreMessage(player.id).toString());
        session.getBasicRemote().sendText(gemsMessage(room.gems).toString());
        session.getBasicRemote().sendText(bombsMessage(room.bombSpawns).toString());
        session.getBasicRemote().sendText(projectilesMessage(room.projectiles).toString());
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
            if ("WAITING".equals(room.state) && room.allPlayersReady() && room.players.size() >= 2) {
                startMatch(ref.matchId, session);
                room.start(getCookieHeader(session));
                scheduleTimer(ref.matchId, room);
            }
            room.broadcast(roomStateMessage(room));
            return;
        }

        if (!"RUNNING".equals(room.state)) {
            return;
        }

        if (isThrowBombMessage(message)) {
            if (throwBomb(room, player)) {
                room.broadcast(playersMessage(Map.of(player.id, player)));
                room.broadcast(projectilesMessage(room.projectiles));
            }
            return;
        }

        MoveResult result = move(room, player, message);
        if (result.moved) {
            room.broadcast(playersMessage(Map.of(player.id, player)));
            if (result.gemPickedUp) {
                room.broadcast(gemsMessage(room.gems));
            }
            if (result.bombPickedUp) {
                room.broadcast(bombsMessage(room.bombSpawns));
            }
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        PlayerRef ref = sessionToPlayer.remove(session);
        if (ref == null) return;

        GameRoom room = rooms.get(ref.matchId);
        if (room == null) return;

        room.sessions.remove(session);
        PlayerState player = room.players.get(ref.playerId);
        if (player == null) return;

        if (room.sessions.containsValue(player.id)) {
            return;
        }

        if (!"RUNNING".equals(room.state)) {
            removePlayerImmediately(room, ref, player);
            return;
        }

        room.scheduleDisconnectRemoval(player.id, () -> removePlayerAfterTimeout(room, ref, player));
    }

    private void removePlayerImmediately(GameRoom room, PlayerRef ref, PlayerState player) {
        player.gems = 0;
        room.players.remove(player.id);
        try {
            room.broadcast(removePlayersMessage(ref.playerId));
            room.broadcast(roomStateMessage(room));
            MatchesEndpoint.broadcastSnapshotsToOpenSessions();
        } catch (IOException exception) {
            System.err.println("Unable to broadcast immediate removal for " + ref.playerId + ": " + exception.getMessage());
        }
        if (room.sessions.isEmpty() && room.players.isEmpty() && "RUNNING".equals(room.state)) {
            finishRoom(ref.matchId, room);
        }
    }

    private void removePlayerAfterTimeout(GameRoom room, PlayerRef ref, PlayerState player) {
        player.gems = 0;
        room.players.remove(player.id);
        try {
            room.broadcast(removePlayersMessage(ref.playerId));
            room.broadcast(roomStateMessage(room));
            MatchesEndpoint.broadcastSnapshotsToOpenSessions();
        } catch (IOException exception) {
            System.err.println("Unable to broadcast delayed removal for " + ref.playerId + ": " + exception.getMessage());
        }
        if (room.sessions.isEmpty() && room.players.isEmpty() && "RUNNING".equals(room.state)) {
            finishRoom(ref.matchId, room);
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

    private void scheduleTimer(String matchId, GameRoom room) {
        room.cancelTimer();
        room.timer = TIMER_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                if (!"RUNNING".equals(room.state)) {
                    return;
                }
                if (room.remainingSeconds() <= 0) {
                    finishRoom(matchId, room);
                    return;
                }
                RoomTickResult tick = room.tick();
                if (tick.bombsChanged) {
                    room.broadcast(bombsMessage(room.bombSpawns));
                }
                if (tick.projectilesChanged) {
                    room.broadcast(projectilesMessage(room.projectiles));
                }
                if (tick.gemsChanged) {
                    room.broadcast(gemsMessage(room.gems));
                }
                if (tick.playersChanged) {
                    room.broadcast(playersMessage(room.players));
                }
                if (tick.remainingSecondsChanged) {
                    room.broadcast(roomStateMessage(room));
                }
            } catch (Exception exception) {
                System.err.println("Game timer error for match " + matchId + ": " + exception.getMessage());
            }
        }, 0, 33, TimeUnit.MILLISECONDS);
    }

    private void finishRoom(String matchId, GameRoom room) {
        if ("FINISHED".equals(room.state)) {
            return;
        }

        room.finish();
        room.projectiles.clear();
        try {
            finishMatch(matchId, room.winnerName, room.finishCookieHeader);
        } catch (Exception exception) {
            System.err.println("Unable to finish match " + matchId + ": " + exception.getMessage());
        }
        try {
            room.broadcast(roomStateMessage(room));
            room.broadcast(projectilesMessage(room.projectiles));
        } catch (IOException exception) {
            System.err.println("Unable to broadcast match end for " + matchId + ": " + exception.getMessage());
        }
        MatchesEndpoint.broadcastSnapshotsToOpenSessions();
    }

    private void finishMatch(String matchId, String winner, String cookieHeader) {
        JSONObject body = new JSONObject();
        body.put("winner", winner == null || winner.isBlank() ? "Inconnu" : winner);
        doPost("/" + matchId + "/finish", body, cookieHeader);
    }

    private void leaveMatch(String matchId, Session session) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + "/" + matchId + "/leave")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .post(Entity.entity("", MediaType.APPLICATION_JSON));
        if (response.getStatus() == 404) {
            response.close();
            return;
        }
        extractBody("/" + matchId + "/leave", response);
    }

    private void doPost(String path, Session session) {
        doPost(path, new JSONObject(), getCookieHeader(session));
    }

    private void doPost(String path, JSONObject body, String cookieHeader) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + path)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", cookieHeader)
                .post(Entity.entity(body == null ? "" : body.toString(), MediaType.APPLICATION_JSON));
        extractBody(path, response);
    }

    private String getCurrentPseudo(Session session) {
        Response response = REST_CLIENT.target(FACADE_API_BASE + "/api/me")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .get();
        String body = extractBody("/api/me", response);
        return new JSONObject(body).optString("pseudo", "Joueur");
    }

    private GameMap loadMapForMatch(String matchId, Session session) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + "/" + matchId)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .get();
        String body = extractBody("/matches/" + matchId, response);
        String mapName = new JSONObject(body).optString("mapName", CLASSIC_MAP.name);
        return loadMapDefinitionFromBackend(mapName, session);
    }

    private GameMap loadMapDefinitionFromBackend(String mapName, Session session) {
        try {
            Response response = REST_CLIENT.target(MATCHES_API_BASE)
                    .path("maps")
                    .path(mapName)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Cookie", getCookieHeader(session))
                    .get();
            String body = extractBody("/matches/maps/" + mapName, response);
            JSONObject json = new JSONObject(body);
            return parseMapDefinition(json);
        } catch (Exception e) {
            System.err.println("Impossible de charger la définition de la map '" + mapName + "' depuis le backend: " + e.getMessage());
            return CLASSIC_MAP;
        }
    }

    private GameMap parseMapDefinition(JSONObject json) {
        String name = json.optString("name", CLASSIC_MAP.name);
        int width = json.optInt("width", CLASSIC_MAP.width);
        int height = json.optInt("height", CLASSIC_MAP.height);
        int gemCount = json.optInt("gemCount", CLASSIC_MAP.gemCount);
        int[][] obstacles = toIntMatrix(json.optJSONArray("obstacles"));
        int[][] startPositions = toIntMatrix(json.optJSONArray("startPositions"));
        if (obstacles.length == 0) {
            obstacles = CLASSIC_MAP.obstacles;
        }
        if (startPositions.length == 0) {
            startPositions = CLASSIC_MAP.startPositions;
        }
        return new GameMap(name, width, height, gemCount, obstacles, startPositions);
    }

    private int[][] toIntMatrix(JSONArray array) {
        if (array == null) {
            return new int[0][0];
        }
        int[][] matrix = new int[array.length()][];
        for (int i = 0; i < array.length(); i++) {
            JSONArray row = array.optJSONArray(i);
            if (row == null) {
                matrix[i] = new int[0];
                continue;
            }
            int[] values = new int[row.length()];
            for (int j = 0; j < row.length(); j++) {
                values[j] = row.optInt(j, 0);
            }
            matrix[i] = values;
        }
        return matrix;
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

    private MoveResult move(GameRoom room, PlayerState player, String message) {
        if (player.isDead()) {
            return MoveResult.noMove();
        }

        MoveVector vector = parseMoveVector(message);
        if (vector.isIdle()) {
            return MoveResult.noMove();
        }

        int nextX = player.x + vector.dx;
        int nextY = player.y + vector.dy;

        if (!canMove(room, player, nextX, nextY)) {
            boolean movedX = vector.dx != 0 && canMove(room, player, player.x + vector.dx, player.y);
            boolean movedY = vector.dy != 0 && canMove(room, player, player.x, player.y + vector.dy);
            if (!movedX && !movedY) {
                return MoveResult.noMove();
            }
            nextX = movedX ? player.x + vector.dx : player.x;
            nextY = movedY ? player.y + vector.dy : player.y;
        }

        player.x = nextX;
        player.y = nextY;
        player.updateFacing(vector.axisX, vector.axisY);
        boolean gemPickedUp = collectGemIfNeeded(room, player);
        boolean bombPickedUp = collectBombIfNeeded(room, player);
        return new MoveResult(true, gemPickedUp, bombPickedUp);
    }

    private MoveVector parseMoveVector(String message) {
        if (message != null && message.trim().startsWith("{")) {
            JSONObject payload = new JSONObject(message);
            if (!"Move".equals(payload.optString("type", ""))) {
                return MoveVector.idle();
            }
            return MoveVector.fromAxes(payload.optInt("dx", 0), payload.optInt("dy", 0));
        }

        switch (message) {
            case "HAUT":
                return MoveVector.fromAxes(0, -1);
            case "BAS":
                return MoveVector.fromAxes(0, 1);
            case "GAUCHE":
                return MoveVector.fromAxes(-1, 0);
            case "DROITE":
                return MoveVector.fromAxes(1, 0);
            default:
                return MoveVector.idle();
        }
    }

    private boolean canMove(GameRoom room, PlayerState movedPlayer, int nextX, int nextY) {
        if (movedPlayer.isDead()) {
            return false;
        }
        if (nextX < 0 || nextY < 0 || nextX + PLAYER_SIZE > room.map.width || nextY + PLAYER_SIZE > room.map.height) {
            return false;
        }

        for (PlayerState player : room.players.values()) {
            if (!player.id.equals(movedPlayer.id) && !player.isDead() && collidesPlayerWithRect(nextX, nextY, player.x + PLAYER_HALF_SIZE, player.y + PLAYER_HALF_SIZE, PLAYER_HALF_SIZE, PLAYER_HALF_SIZE)
                    && !isEscapingPlayerOverlap(movedPlayer, player, nextX, nextY)) {
                return false;
            }
        }

        for (int[] obstacle : room.map.obstacles) {
            if (collidesPlayerWithRect(nextX, nextY, obstacle[0], obstacle[1], obstacle[2], obstacle[3])) {
                return false;
            }
        }

        return true;
    }

    private boolean isEscapingPlayerOverlap(PlayerState movedPlayer, PlayerState otherPlayer, int nextX, int nextY) {
        if (!collidesPlayerWithRect(movedPlayer.x, movedPlayer.y, otherPlayer.x + PLAYER_HALF_SIZE, otherPlayer.y + PLAYER_HALF_SIZE, PLAYER_HALF_SIZE, PLAYER_HALF_SIZE)) {
            return false;
        }

        return overlapArea(nextX, nextY, otherPlayer) < overlapArea(movedPlayer.x, movedPlayer.y, otherPlayer);
    }

    private int overlapArea(int playerLeft, int playerTop, PlayerState otherPlayer) {
        int overlapX = Math.max(0, Math.min(playerLeft + PLAYER_SIZE, otherPlayer.x + PLAYER_SIZE) - Math.max(playerLeft, otherPlayer.x));
        int overlapY = Math.max(0, Math.min(playerTop + PLAYER_SIZE, otherPlayer.y + PLAYER_SIZE) - Math.max(playerTop, otherPlayer.y));
        return overlapX * overlapY;
    }

    private boolean collectGemIfNeeded(GameRoom room, PlayerState player) {
        if (player.isDead()) {
            return false;
        }

        String collectedId = null;
        for (GemState gem : room.gems.values()) {
            if (collidesPlayerWithRect(player.x, player.y, gem.x + GEM_HALF_SIZE, gem.y + GEM_HALF_SIZE, GEM_HALF_SIZE, GEM_HALF_SIZE)) {
                collectedId = gem.id;
                break;
            }
        }

        if (collectedId == null) {
            return false;
        }

        room.gems.remove(collectedId);
        player.gems++;
        room.spawnMissingGems();
        return true;
    }

    private boolean collectBombIfNeeded(GameRoom room, PlayerState player) {
        if (player.hasBomb || player.isDead()) {
            return false;
        }

        for (BombSpawnState spawn : room.bombSpawns.values()) {
            if (spawn.available && collidesPlayerWithRect(player.x, player.y, spawn.x + BOMB_HALF_SIZE, spawn.y + BOMB_HALF_SIZE, BOMB_HALF_SIZE, BOMB_HALF_SIZE)) {
                spawn.pickup();
                player.hasBomb = true;
                return true;
            }
        }

        return false;
    }

    private boolean isThrowBombMessage(String message) {
        if ("ATTAQUE".equals(message)) {
            return true;
        }
        if (message != null && message.trim().startsWith("{")) {
            JSONObject payload = new JSONObject(message);
            return "ThrowBomb".equals(payload.optString("type", ""));
        }
        return false;
    }

    private boolean throwBomb(GameRoom room, PlayerState player) {
        if (!player.hasBomb || player.isDead()) {
            return false;
        }

        player.hasBomb = false;
        room.addProjectile(player);
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
            JSONObject playerData = new JSONObject();
            playerData.put("x", player.x);
            playerData.put("y", player.y);
            playerData.put("color", player.color);
            playerData.put("pseudo", player.pseudo);
            playerData.put("gems", player.gems);
            playerData.put("hasBomb", player.hasBomb);
            playerData.put("dead", player.isDead());
            data.put(player.id, playerData);
        }

        JSONObject json = new JSONObject();
        json.put("type", "Players");
        json.put("data", data);
        return json;
    }

    private JSONObject mapMessage(GameMap map) {
        JSONArray obstacles = new JSONArray();
        for (int[] obstacle : map.obstacles) {
            JSONObject obstacleData = new JSONObject();
            obstacleData.put("x", obstacle[0] - obstacle[2]);
            obstacleData.put("y", obstacle[1] - obstacle[3]);
            obstacleData.put("width", obstacle[2] * 2);
            obstacleData.put("height", obstacle[3] * 2);
            obstacles.put(obstacleData);
        }

        JSONObject data = new JSONObject();
        data.put("name", map.name);
        data.put("width", map.width);
        data.put("height", map.height);
        data.put("gemCount", map.gemCount);
        data.put("obstacles", obstacles);

        JSONObject json = new JSONObject();
        json.put("type", "Map");
        json.put("data", data);
        return json;
    }

    private JSONObject youAreMessage(String playerId) {
        JSONObject json = new JSONObject();
        json.put("type", "YouAre");
        json.put("playerId", playerId);
        return json;
    }

    private static JSONObject roomStateMessage(GameRoom room) {
        JSONObject ready = new JSONObject();
        for (PlayerState player : room.players.values()) {
            ready.put(player.id, player.ready);
        }

        JSONObject json = new JSONObject();
        json.put("type", "RoomState");
        json.put("state", room.state);
        json.put("ready", ready);
        json.put("playersCount", room.players.size());
        json.put("remainingSeconds", room.remainingSeconds());
        json.put("durationSeconds", MATCH_DURATION_SECONDS);
        json.put("winnerName", room.winnerName == null ? "" : room.winnerName);
        return json;
    }

    private static JSONObject removePlayersMessage(String playerId) {
        ArrayList<String> ids = new ArrayList<>();
        ids.add(playerId);

        JSONObject json = new JSONObject();
        json.put("type", "RemovePlayers");
        json.put("data", new JSONArray(ids));
        return json;
    }

    private static JSONObject notificationMessage(String message) {
        JSONObject json = new JSONObject();
        json.put("type", "Notification");
        json.put("message", message);
        return json;
    }

    private JSONObject gemsMessage(Map<String, GemState> gems) {
        JSONObject data = new JSONObject();
        for (GemState gem : gems.values()) {
            JSONObject gemData = new JSONObject();
            gemData.put("x", gem.x);
            gemData.put("y", gem.y);
            data.put(gem.id, gemData);
        }

        JSONObject json = new JSONObject();
        json.put("type", "Gems");
        json.put("data", data);
        return json;
    }

    private JSONObject bombsMessage(Map<String, BombSpawnState> bombSpawns) {
        JSONObject data = new JSONObject();
        for (BombSpawnState spawn : bombSpawns.values()) {
            JSONObject spawnData = new JSONObject();
            spawnData.put("x", spawn.x);
            spawnData.put("y", spawn.y);
            spawnData.put("available", spawn.available);
            spawnData.put("cooldownMs", spawn.cooldownMs());
            data.put(spawn.id, spawnData);
        }

        JSONObject json = new JSONObject();
        json.put("type", "BombSpawns");
        json.put("data", data);
        return json;
    }

    private JSONObject projectilesMessage(Map<String, ProjectileState> projectiles) {
        JSONObject data = new JSONObject();
        for (ProjectileState projectile : projectiles.values()) {
            JSONObject projectileData = new JSONObject();
            projectileData.put("x", projectile.x);
            projectileData.put("y", projectile.y);
            data.put(projectile.id, projectileData);
        }

        JSONObject json = new JSONObject();
        json.put("type", "Projectiles");
        json.put("data", data);
        return json;
    }

    private static class GameRoom {
        private final GameMap map;
        private final Map<Session, String> sessions = new ConcurrentHashMap<>();
        private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
        private final Map<String, PlayerState> knownPlayers = new ConcurrentHashMap<>();
        private final Map<String, GemState> gems = new ConcurrentHashMap<>();
        private final Map<String, BombSpawnState> bombSpawns = new ConcurrentHashMap<>();
        private final Map<String, ProjectileState> projectiles = new ConcurrentHashMap<>();
        private final Map<String, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();
        private final AtomicInteger nextPlayerId = new AtomicInteger(0);
        private final AtomicInteger nextGemId = new AtomicInteger(0);
        private final AtomicInteger nextProjectileId = new AtomicInteger(0);
        private final Random random = new Random();
        private String state = "WAITING";
        private long startedAtMillis = 0;
        private long endsAtMillis = 0;
        private String winnerName = "";
        private String finishCookieHeader = "";
        private ScheduledFuture<?> timer;
        private int lastBroadcastRemainingSeconds = MATCH_DURATION_SECONDS;

        private GameRoom(GameMap map) {
            this.map = map;
            initBombSpawns();
            spawnMissingGems();
        }

        private void initBombSpawns() {
            for (int i = 0; i < BOMB_SPAWN_POINTS.length; i++) {
                int[] point = BOMB_SPAWN_POINTS[i];
                if (point[0] >= 0 && point[1] >= 0 && point[0] + BOMB_SIZE <= map.width && point[1] + BOMB_SIZE <= map.height) {
                    bombSpawns.put("b" + i, new BombSpawnState("b" + i, point[0], point[1]));
                }
            }
        }

        private PlayerState addPlayer(String pseudo) {
            int id = nextPlayerId.getAndIncrement();
            int[] startPosition = map.startPosition(id);
            PlayerState player = new PlayerState("p" + id, pseudo, startPosition[0], startPosition[1], PLAYER_COLORS[id % PLAYER_COLORS.length]);
            players.put(player.id, player);
            knownPlayers.put(player.id, player);
            removeGemsCollidingWith(player);
            spawnMissingGems();
            return player;
        }

        private PlayerState findDisconnectedPlayer(String pseudo) {
            for (PlayerState player : knownPlayers.values()) {
                if (player.pseudo.equals(pseudo) && !sessions.containsValue(player.id)) {
                    return player;
                }
            }
            return null;
        }

        private void reconnectPlayer(Session session, PlayerState player) {
            sessions.put(session, player.id);
            players.put(player.id, player);
            cancelDisconnectTimer(player.id);
        }

        private void scheduleDisconnectRemoval(String playerId, Runnable removalTask) {
            cancelDisconnectTimer(playerId);
            ScheduledFuture<?> timer = TIMER_EXECUTOR.schedule(() -> {
                disconnectTimers.remove(playerId);
                removalTask.run();
            }, RECONNECT_GRACE_SECONDS, TimeUnit.SECONDS);
            disconnectTimers.put(playerId, timer);
        }

        private void cancelDisconnectTimer(String playerId) {
            ScheduledFuture<?> timer = disconnectTimers.remove(playerId);
            if (timer != null) {
                timer.cancel(false);
            }
        }

        private void cancelTimerForAllPlayers() {
            for (ScheduledFuture<?> timer : disconnectTimers.values()) {
                if (timer != null) {
                    timer.cancel(false);
                }
            }
            disconnectTimers.clear();
        }

        private void cancelForCancellation() throws IOException {
            cancelTimerForAllPlayers();
            this.state = "FINISHED";
            this.winnerName = "Annulé";
            this.projectiles.clear();
            broadcast(roomStateMessage(this));
            broadcast(notificationMessage("La partie a été annulée."));
        }

        private void removeGemsCollidingWith(PlayerState player) {
            ArrayList<String> removedGemIds = new ArrayList<>();
            for (GemState gem : gems.values()) {
                boolean onPlayer = Math.abs(player.x + PLAYER_HALF_SIZE - (gem.x + GEM_HALF_SIZE)) < PLAYER_HALF_SIZE + GEM_HALF_SIZE
                    && Math.abs(player.y + PLAYER_HALF_SIZE - (gem.y + GEM_HALF_SIZE)) < PLAYER_HALF_SIZE + GEM_HALF_SIZE;
                if (onPlayer) {
                    removedGemIds.add(gem.id);
                }
            }

            for (String gemId : removedGemIds) {
                gems.remove(gemId);
            }
        }

        private void spawnMissingGems() {
            while (gems.size() < map.gemCount) {
                GemState gem = randomGem();
                if (gem != null) {
                    gems.put(gem.id, gem);
                } else {
                    return;
                }
            }
        }

        private GemState randomGem() {
            for (int attempt = 0; attempt < 100; attempt++) {
                int x = random.nextInt(map.width - GEM_SIZE);
                int y = random.nextInt(map.height - GEM_SIZE);
                if (isValidGemPosition(x, y)) {
                    return new GemState("g" + nextGemId.getAndIncrement(), x, y);
                }
            }
            return null;
        }

        private boolean isValidGemPosition(int x, int y) {
            int centerX = x + GEM_HALF_SIZE;
            int centerY = y + GEM_HALF_SIZE;

            for (int[] obstacle : map.obstacles) {
                boolean inObstacle = Math.abs(obstacle[0] - centerX) < obstacle[2] + GEM_HALF_SIZE
                    && Math.abs(obstacle[1] - centerY) < obstacle[3] + GEM_HALF_SIZE;
                if (inObstacle) {
                    return false;
                }
            }

            for (PlayerState player : players.values()) {
                boolean onPlayer = Math.abs(player.x + PLAYER_HALF_SIZE - centerX) < PLAYER_HALF_SIZE + GEM_HALF_SIZE
                    && Math.abs(player.y + PLAYER_HALF_SIZE - centerY) < PLAYER_HALF_SIZE + GEM_HALF_SIZE;
                if (onPlayer) {
                    return false;
                }
            }

            for (GemState gem : gems.values()) {
                boolean onGem = Math.abs(gem.x + GEM_HALF_SIZE - centerX) < GEM_SIZE
                    && Math.abs(gem.y + GEM_HALF_SIZE - centerY) < GEM_SIZE;
                if (onGem) {
                    return false;
                }
            }

            return true;
        }

        private boolean allPlayersReady() {
            return !players.isEmpty() && players.values().stream().allMatch(player -> player.ready);
        }

        private void start(String cookieHeader) {
            this.state = "RUNNING";
            this.startedAtMillis = System.currentTimeMillis();
            this.endsAtMillis = this.startedAtMillis + MATCH_DURATION_SECONDS * 1000L;
            this.finishCookieHeader = cookieHeader;
            this.lastBroadcastRemainingSeconds = MATCH_DURATION_SECONDS;
        }

        private int remainingSeconds() {
            if ("WAITING".equals(state)) {
                return MATCH_DURATION_SECONDS;
            }
            if (endsAtMillis == 0) {
                return 0;
            }
            long remainingMillis = endsAtMillis - System.currentTimeMillis();
            return (int) Math.max(0, Math.ceil(remainingMillis / 1000.0));
        }

        private void finish() {
            this.state = "FINISHED";
            this.winnerName = resolveWinnerName();
            this.cancelTimer();
        }

        private String resolveWinnerName() {
            PlayerState winner = null;
            for (PlayerState player : knownPlayers.values()) {
                if (winner == null || player.gems > winner.gems) {
                    winner = player;
                }
            }
            return winner == null ? "Inconnu" : winner.pseudo;
        }

        private void cancelTimer() {
            if (timer != null) {
                timer.cancel(false);
                timer = null;
            }
        }

        private void addProjectile(PlayerState player) {
            int directionX = player.facingX;
            int directionY = player.facingY;
            int speedX = directionX * PROJECTILE_SPEED;
            int speedY = directionY * PROJECTILE_SPEED;
            if (directionX != 0 && directionY != 0) {
                int diagonalSpeed = (int) Math.round(PROJECTILE_SPEED / Math.sqrt(2));
                speedX = directionX * diagonalSpeed;
                speedY = directionY * diagonalSpeed;
            }

            int projectileX = player.x + PLAYER_HALF_SIZE - BOMB_HALF_SIZE + directionX * PLAYER_HALF_SIZE;
            int projectileY = player.y + PLAYER_HALF_SIZE - BOMB_HALF_SIZE + directionY * PLAYER_HALF_SIZE;
            ProjectileState projectile = new ProjectileState(
                "bp" + nextProjectileId.getAndIncrement(),
                player.id,
                projectileX,
                projectileY,
                speedX,
                speedY
            );
            projectiles.put(projectile.id, projectile);
        }

        private RoomTickResult tick() {
            boolean bombsChanged = refreshBombSpawns();
            boolean deadStatesChanged = refreshDeadPlayers();
            ProjectileUpdateResult projectileResult = updateProjectiles();
            int remaining = remainingSeconds();
            boolean remainingSecondsChanged = remaining != lastBroadcastRemainingSeconds;
            lastBroadcastRemainingSeconds = remaining;
            return new RoomTickResult(
                bombsChanged,
                projectileResult.projectilesChanged,
                projectileResult.gemsChanged,
                projectileResult.playersChanged || deadStatesChanged,
                remainingSecondsChanged
            );
        }

        private boolean refreshDeadPlayers() {
            boolean changed = false;
            for (PlayerState player : players.values()) {
                if (player.reviveIfNeeded()) {
                    changed = true;
                }
            }
            return changed;
        }

        private boolean refreshBombSpawns() {
            boolean changed = false;
            long now = System.currentTimeMillis();
            for (BombSpawnState spawn : bombSpawns.values()) {
                if (!spawn.available && now >= spawn.availableAtMillis) {
                    spawn.available = true;
                    changed = true;
                }
            }
            return changed;
        }

        private ProjectileUpdateResult updateProjectiles() {
            if (projectiles.isEmpty()) {
                return new ProjectileUpdateResult(false, false, false);
            }

            boolean playersChanged = false;
            boolean gemsChanged = false;
            ArrayList<String> removedProjectileIds = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (ProjectileState projectile : projectiles.values()) {
                projectile.x += projectile.vx;
                projectile.y += projectile.vy;

                if (now - projectile.createdAtMillis > PROJECTILE_MAX_AGE_MS || projectileOutOfBounds(projectile) || projectileHitsObstacle(projectile)) {
                    removedProjectileIds.add(projectile.id);
                    continue;
                }

                PlayerState hitPlayer = hitPlayer(projectile);
                if (hitPlayer != null) {
                    int droppedGems = Math.min(GEMS_DROPPED_ON_HIT, hitPlayer.gems);
                    hitPlayer.gems -= droppedGems;
                    hitPlayer.kill();
                    dropGemsAround(hitPlayer, droppedGems);
                    playersChanged = true;
                    gemsChanged = droppedGems > 0;
                    removedProjectileIds.add(projectile.id);
                }
            }

            for (String projectileId : removedProjectileIds) {
                projectiles.remove(projectileId);
            }

            return new ProjectileUpdateResult(true, playersChanged, gemsChanged);
        }

        private void dropGemsAround(PlayerState player, int count) {
            for (int i = 0; i < count; i++) {
                GemState gem = droppedGemNear(player, i, count);
                if (gem != null) {
                    gems.put(gem.id, gem);
                }
            }
        }

        private GemState droppedGemNear(PlayerState player, int index, int total) {
            double baseAngle = (2 * Math.PI * index) / Math.max(1, total);
            int centerX = player.x + PLAYER_HALF_SIZE;
            int centerY = player.y + PLAYER_HALF_SIZE;

            for (int attempt = 0; attempt < 80; attempt++) {
                double angle = baseAngle + random.nextDouble() * Math.PI / 3;
                int distance = 48 + random.nextInt(44);
                int x = centerX - GEM_HALF_SIZE + (int) Math.round(Math.cos(angle) * distance);
                int y = centerY - GEM_HALF_SIZE + (int) Math.round(Math.sin(angle) * distance);
                x = Math.max(0, Math.min(map.width - GEM_SIZE, x));
                y = Math.max(0, Math.min(map.height - GEM_SIZE, y));
                if (isValidGemPosition(x, y)) {
                    return new GemState("g" + nextGemId.getAndIncrement(), x, y);
                }
            }

            return null;
        }

        private boolean projectileOutOfBounds(ProjectileState projectile) {
            return projectile.x < 0
                || projectile.y < 0
                || projectile.x + BOMB_SIZE > map.width
                || projectile.y + BOMB_SIZE > map.height;
        }

        private boolean projectileHitsObstacle(ProjectileState projectile) {
            for (int[] obstacle : map.obstacles) {
                boolean hit = Math.abs(obstacle[0] - (projectile.x + BOMB_HALF_SIZE)) < obstacle[2] + BOMB_HALF_SIZE
                    && Math.abs(obstacle[1] - (projectile.y + BOMB_HALF_SIZE)) < obstacle[3] + BOMB_HALF_SIZE;
                if (hit) {
                    return true;
                }
            }
            return false;
        }

        private PlayerState hitPlayer(ProjectileState projectile) {
            for (PlayerState player : players.values()) {
                if (projectile.ownerId.equals(player.id) || player.isDead()) {
                    continue;
                }
                boolean hit = Math.abs(player.x + PLAYER_HALF_SIZE - (projectile.x + BOMB_HALF_SIZE)) < PLAYER_HALF_SIZE + BOMB_HALF_SIZE
                    && Math.abs(player.y + PLAYER_HALF_SIZE - (projectile.y + BOMB_HALF_SIZE)) < PLAYER_HALF_SIZE + BOMB_HALF_SIZE;
                if (hit) {
                    return player;
                }
            }
            return null;
        }

        private void broadcast(JSONObject json) throws IOException {
            for (Session session : sessions.keySet()) {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(json.toString());
                }
            }
        }
    }

    private static class GameMap {
        private final String name;
        private final int width;
        private final int height;
        private final int gemCount;
        private final int[][] obstacles;
        private final int[][] startPositions;

        private GameMap(String name, int width, int height, int gemCount, int[][] obstacles, int[][] startPositions) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.gemCount = gemCount;
            this.obstacles = obstacles;
            this.startPositions = startPositions;
        }

        private int[] startPosition(int playerIndex) {
            return startPositions[playerIndex % startPositions.length];
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
        private final String id;
        private final String pseudo;
        private final String color;
        private int x;
        private int y;
        private int gems;
        private boolean ready;
        private boolean hasBomb;
        private int facingX;
        private int facingY;
        private long deadUntilMillis;

        private PlayerState(String id, String pseudo, int x, int y, String color) {
            this.id = id;
            this.pseudo = pseudo;
            this.color = color;
            this.x = x;
            this.y = y;
            this.gems = 0;
            this.ready = false;
            this.hasBomb = false;
            this.facingX = 1;
            this.facingY = 0;
            this.deadUntilMillis = 0;
        }

        private void updateFacing(int axisX, int axisY) {
            if (axisX != 0 || axisY != 0) {
                this.facingX = axisX;
                this.facingY = axisY;
            }
        }

        private boolean isDead() {
            return deadUntilMillis > System.currentTimeMillis();
        }

        private void kill() {
            this.deadUntilMillis = System.currentTimeMillis() + PLAYER_DEAD_DURATION_MS;
        }

        private boolean reviveIfNeeded() {
            if (deadUntilMillis == 0 || System.currentTimeMillis() < deadUntilMillis) {
                return false;
            }
            deadUntilMillis = 0;
            return true;
        }
    }

    private static class MoveVector {
        private static final int WALK_STEP = 6;

        private final int dx;
        private final int dy;
        private final int axisX;
        private final int axisY;

        private MoveVector(int dx, int dy, int axisX, int axisY) {
            this.dx = dx;
            this.dy = dy;
            this.axisX = axisX;
            this.axisY = axisY;
        }

        private static MoveVector fromAxes(int axisX, int axisY) {
            int normalizedX = Integer.compare(axisX, 0);
            int normalizedY = Integer.compare(axisY, 0);
            if (normalizedX == 0 && normalizedY == 0) {
                return idle();
            }

            if (normalizedX != 0 && normalizedY != 0) {
                int diagonalStep = (int) Math.round(WALK_STEP / Math.sqrt(2));
                return new MoveVector(normalizedX * diagonalStep, normalizedY * diagonalStep, normalizedX, normalizedY);
            }

            return new MoveVector(normalizedX * WALK_STEP, normalizedY * WALK_STEP, normalizedX, normalizedY);
        }

        private static MoveVector idle() {
            return new MoveVector(0, 0, 0, 0);
        }

        private boolean isIdle() {
            return dx == 0 && dy == 0;
        }
    }

    private static class BombSpawnState {
        private final String id;
        private final int x;
        private final int y;
        private boolean available = true;
        private long availableAtMillis = 0;

        private BombSpawnState(String id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }

        private void pickup() {
            this.available = false;
            this.availableAtMillis = System.currentTimeMillis() + BOMB_SPAWN_COOLDOWN_MS;
        }

        private int cooldownMs() {
            if (available) {
                return 0;
            }
            return (int) Math.max(0, availableAtMillis - System.currentTimeMillis());
        }
    }

    private static class ProjectileState {
        private final String id;
        private final String ownerId;
        private int x;
        private int y;
        private final int vx;
        private final int vy;
        private final long createdAtMillis;

        private ProjectileState(String id, String ownerId, int x, int y, int vx, int vy) {
            this.id = id;
            this.ownerId = ownerId;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.createdAtMillis = System.currentTimeMillis();
        }
    }

    private static class GemState {
        private final String id;
        private final int x;
        private final int y;

        private GemState(String id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    private static class MoveResult {
        private final boolean moved;
        private final boolean gemPickedUp;
        private final boolean bombPickedUp;

        private MoveResult(boolean moved, boolean gemPickedUp, boolean bombPickedUp) {
            this.moved = moved;
            this.gemPickedUp = gemPickedUp;
            this.bombPickedUp = bombPickedUp;
        }

        private static MoveResult noMove() {
            return new MoveResult(false, false, false);
        }
    }

    private static class RoomTickResult {
        private final boolean bombsChanged;
        private final boolean projectilesChanged;
        private final boolean gemsChanged;
        private final boolean playersChanged;
        private final boolean remainingSecondsChanged;

        private RoomTickResult(boolean bombsChanged, boolean projectilesChanged, boolean gemsChanged, boolean playersChanged, boolean remainingSecondsChanged) {
            this.bombsChanged = bombsChanged;
            this.projectilesChanged = projectilesChanged;
            this.gemsChanged = gemsChanged;
            this.playersChanged = playersChanged;
            this.remainingSecondsChanged = remainingSecondsChanged;
        }
    }

    private static class ProjectileUpdateResult {
        private final boolean projectilesChanged;
        private final boolean playersChanged;
        private final boolean gemsChanged;

        private ProjectileUpdateResult(boolean projectilesChanged, boolean playersChanged, boolean gemsChanged) {
            this.projectilesChanged = projectilesChanged;
            this.playersChanged = playersChanged;
            this.gemsChanged = gemsChanged;
        }
    }
}
