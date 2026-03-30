package zelda.facade;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws")
public class ChatEndpoint{
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    
    @OnOpen
    public void onOpen(Session session) throws IOException {
        sessions.add(session);
        session.getBasicRemote().sendText("[server] welcome " + session.getId());
        broadcast("[server] " + session.getId() + " joined the chat");
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        broadcast("[" + session.getId() + "] " + message);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        sessions.remove(session);
        broadcast("[server] " + session.getId() + " left the chat");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket error for session " + session.getId() + ":");
    }

    private void broadcast(String message) throws IOException {
        for (Session s : sessions) {
            if (s.isOpen()) {
                s.getBasicRemote().sendText(message);
            }
        }
    }
}