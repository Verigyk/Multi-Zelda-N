package pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import static java.util.Map.entry;

import org.json.JSONObject;
import org.json.JSONArray;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/GameEndpoint/ws")
public class GameEndpoint{
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    private static ConcurrentHashMap<Session, String> sessionToPlayer = new ConcurrentHashMap<Session, String>(); 
    private static ConcurrentHashMap<String, Integer> nbSessionsToAccount = new ConcurrentHashMap<String, Integer>();

    private static int n = 0;

    private static ModelMatch modelMatch = new ModelMatch();

    @OnOpen
    public void onOpen(Session session) throws IOException {
        
        sessions.add(session);

        String id = Integer.toString(n);
        n += 1;

        if (nbSessionsToAccount.containsKey(id)) {
            nbSessionsToAccount.put(id, nbSessionsToAccount.get(id));
        } else {
            nbSessionsToAccount.put(id, 1);
        }

        JSONObject oldJSONPlyersData = this.getJSONPlayersData(modelMatch.getCoordinatesPlayers(), "Players");
        session.getBasicRemote().sendText(oldJSONPlyersData.toString());

        sessionToPlayer.put(session, id);
        modelMatch.addPlayer(id);

        JSONObject JSON_new_player = this.getJSONPlayersData(
            modelMatch.getInformationPlayer(id),
            "Players");

        broadcast(JSON_new_player);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        String id = sessionToPlayer.get(session);

        modelMatch.move(id, message);

        JSONObject json = new JSONObject();

        json.put("data", new JSONObject(
            modelMatch.getInformationPlayer(id)
        ));

        json.put("type", "Players");

        broadcast(json);
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        String id = sessionToPlayer.get(session);

        sessions.remove(session);
        sessionToPlayer.remove(session);

        if (nbSessionsToAccount.get(id) == 1) {
            nbSessionsToAccount.remove(id);
            modelMatch.removePlayer(id);

            ArrayList<String> remove_ids = new ArrayList<String>();
            remove_ids.add(id);

            JSONObject removePlayerJSON = this.getJSONPlayersData(remove_ids, "RemovePlayers");

            broadcast(removePlayerJSON);
        } else {
            nbSessionsToAccount.put(id, nbSessionsToAccount.get(id) - 1);
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

    private JSONObject getJSONPlayersData(Map<?, ?> playersdata, String type) {
        JSONObject json = new JSONObject();

        json.put("data", new JSONObject(playersdata));
        json.put("type", type);

        return json;
    }

    private JSONObject getJSONPlayersData(Collection<?> playersdata, String type) {
        JSONObject json = new JSONObject();

        json.put("data", new JSONArray(playersdata));
        json.put("type", type);

        return json;
    }
}
