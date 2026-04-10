package pack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import static java.util.Map.entry;

import org.json.JSONObject;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/GameEndpoint/ws")
public class GameEndpoint{
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    private static ConcurrentHashMap<String, int[]> coordinatesPlayers = new ConcurrentHashMap<String, int[]>();
    private static ConcurrentHashMap<Session, String> sessionToPlayer = new ConcurrentHashMap<Session, String>(); 

    private static int coordinate_x = 50;

    @OnOpen
    public void onOpen(Session session) throws IOException {
        
        sessions.add(session);

        String id = Integer.toString(sessions.size());
        int[] new_player_position = new int[]{coordinate_x, 50};

        coordinate_x += 50;

        JSONObject oldJSONPlyersData = this.getJSONPlayersData(coordinatesPlayers);
        session.getBasicRemote().sendText(oldJSONPlyersData.toString());

        sessionToPlayer.put(session, id);
        coordinatesPlayers.put(id, new_player_position);

        JSONObject JSON_new_player = this.getJSONPlayersData(Map.ofEntries(
            entry(id, new_player_position)
        ));
        broadcast(JSON_new_player);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        String id = sessionToPlayer.get(session);

        switch (message) {
            case "HAUT":
                coordinatesPlayers.get(id)[1] -= 5;
                break;
            case "BAS":
                coordinatesPlayers.get(id)[1] += 5;
                break;
            case "GAUCHE":
                coordinatesPlayers.get(id)[0] -= 5;
                break;
            case "DROITE":
                coordinatesPlayers.get(id)[0] += 5;
                break;
        }

        JSONObject json = new JSONObject();

        json.put("data", new JSONObject(Map.ofEntries(
            entry(id, coordinatesPlayers.get(id))
        )));

        json.put("type", "Players");

        broadcast(json);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        sessions.remove(session);
        sessionToPlayer.remove(session);
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

    private JSONObject getJSONPlayersData(Map playersdata) {
        JSONObject json = new JSONObject();

        json.put("data", new JSONObject(coordinatesPlayers));
        json.put("type", "Players");

        return json;
    }
}
