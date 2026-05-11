package pack;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws")
public class ChatEndpoint{
    private static final long RECONNECT_GRACE_MS = 2000;
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private static final Map<Session, String> names = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentlyLeft = new ConcurrentHashMap<>();
    private String myName = "server";

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        JSONObject json = new JSONObject(message);
        String type = json.optString("type", "message");

        if ("join".equals(type)) {
            String displayName = cleanName(json.optString("displayName", json.optString("senderId", "")));
            names.put(session, displayName);
            Long leftAt = recentlyLeft.remove(displayName);
            if (leftAt == null || Instant.now().toEpochMilli() - leftAt > RECONNECT_GRACE_MS) {
                broadcastServer(displayName + " joined the chat");
            }
            return;
        }

        broadcast(json);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        sessions.remove(session);
        String displayName = names.remove(session);

        if (displayName != null) {
            recentlyLeft.put(displayName, Instant.now().toEpochMilli());
            new Thread(() -> broadcastLeaveIfStillDisconnected(displayName)).start();
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket error for session " + session.getId() + ":");
    }

    private void broadcast(JSONObject json) throws IOException {
        // Broadcast à tous les clients
        for (Session client : sessions) {
            client.getBasicRemote().sendText(json.toString());
        }
    }

    private void broadcastServer(String text) throws IOException {
        JSONObject json = new JSONObject();
        json.put("senderId", myName);
        json.put("displayName", myName);
        json.put("text", text);
        broadcast(json);
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.trim();
    }

    private void broadcastLeaveIfStillDisconnected(String displayName) {
        try {
            Thread.sleep(RECONNECT_GRACE_MS);
            Long leftAt = recentlyLeft.remove(displayName);
            if (leftAt != null) {
                broadcastServer(displayName + " left the chat");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            System.err.println("Unable to broadcast chat leave event: " + exception.getMessage());
        }
    }
}
