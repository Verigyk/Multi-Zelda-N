package pack;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@ServerEndpoint(value = "/matches/ws", configurator = MatchesEndpoint.Config.class)
public class MatchesEndpoint {
    private static final String COOKIE_HEADER_KEY = "cookieHeader";
    private static final String MATCHES_API_BASE = System.getProperty("facade.matches.url",
            "http://localhost:8080/facade/matches");
    private static final Client REST_CLIENT = new ResteasyClientBuilder().build();
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

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
    public void onOpen(Session session) {
        sessions.add(session);
        try {
            sendSnapshot(session);
        } catch (Exception exception) {
            sendError(session, exception.getMessage());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            JSONObject payload = new JSONObject(message);
            String action = payload.optString("action", "");

            switch (action) {
                case "refresh":
                    sendSnapshot(session);
                    return;
                case "create":
                    handleCreate(payload, session);
                    break;
                case "join":
                    handleIdAction(payload, session, "join");
                    break;
                case "start":
                    handleIdAction(payload, session, "start");
                    break;
                case "finish":
                    handleFinish(payload, session);
                    break;
                default:
                    sendError(session, "Action inconnue: " + action);
                    return;
            }

            broadcastSnapshots();
        } catch (Exception exception) {
            sendError(session, exception.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String sessionId = session == null ? "unknown" : session.getId();
        System.err.println("Matches WS error for session " + sessionId + ": " + throwable.getMessage());
    }

    private void handleCreate(JSONObject payload, Session session) {
        JSONObject request = new JSONObject();
        if (payload.has("title")) {
            request.put("title", payload.optString("title", ""));
        }
        if (payload.has("maxPlayers")) {
            request.put("maxPlayers", payload.optInt("maxPlayers", 4));
        }
        doPost("/create", request, session);
    }

    private void handleIdAction(JSONObject payload, Session session, String action) {
        String id = payload.optString("id", "").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id manquant");
        }
        doPost("/" + id + "/" + action, null, session);
    }

    private void handleFinish(JSONObject payload, Session session) {
        String id = payload.optString("id", "").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id manquant");
        }

        JSONObject request = new JSONObject();
        request.put("winner", payload.optString("winner", ""));
        doPost("/" + id + "/finish", request, session);
    }

    private void sendSnapshot(Session session) {
        String active = doGet("/active", session);
        String history = doGet("/history", session);

        JSONObject message = new JSONObject();
        message.put("type", "snapshot");
        message.put("active", new JSONArray(active));
        message.put("history", new JSONArray(history));
        send(session, message);
    }

    private void broadcastSnapshots() {
        for (Session session : sessions) {
            try {
                sendSnapshot(session);
            } catch (Exception exception) {
                sendError(session, exception.getMessage());
            }
        }
    }

    private String doGet(String path, Session session) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + path)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .get();
        return extractBody(path, response);
    }

    private void doPost(String path, JSONObject body, Session session) {
        Response response = REST_CLIENT.target(MATCHES_API_BASE + path)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", getCookieHeader(session))
                .post(Entity.entity(body == null ? "" : body.toString(), MediaType.APPLICATION_JSON));
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
            throw new RuntimeException("Token absent: connecte-toi via /facade/auth/login");
        }
        return cookieHeader.toString();
    }

    private void sendError(Session session, String message) {
        JSONObject error = new JSONObject();
        error.put("type", "error");
        error.put("message", message);
        send(session, error);
    }

    private void send(Session session, JSONObject message) {
        try {
            session.getBasicRemote().sendText(message.toString());
        } catch (IOException exception) {
            throw new RuntimeException("Envoi WS impossible");
        }
    }
}
