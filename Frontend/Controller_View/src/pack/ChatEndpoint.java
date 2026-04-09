package pack;

import java.io.IOException;
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
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private String myName = "server";

    @OnOpen
    public void onOpen(Session session) throws IOException {
        JSONObject json = new JSONObject();
        json.put("senderId", myName);
        json.put("text", "welcome " + session.getId());
        
        sessions.add(session);
        session.getBasicRemote().sendText(json.toString());

        json.put("text", session.getId() + " joined the chat");
        broadcast(json);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        JSONObject json = new JSONObject(message);

        broadcast(json);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        sessions.remove(session);
        
        JSONObject json = new JSONObject();
        json.put("senderId", myName);
        json.put("text", session.getId() + " left the chat");

        broadcast(json);
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
}