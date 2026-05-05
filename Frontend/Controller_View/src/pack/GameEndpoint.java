package pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;

import org.json.JSONObject;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.json.JSONArray;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import pack.backendObjects.Player;

@ServerEndpoint("/GameEndpoint/ws")
public class GameEndpoint{
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    private static ConcurrentHashMap<Session, String> sessionToPlayer = new ConcurrentHashMap<Session, String>(); 
    private static ConcurrentHashMap<String, Integer> nbSessionsToAccount = new ConcurrentHashMap<String, Integer>();

    private static int n = 0;

    private static final String path = "http://localhost:8080/facade/activeMatch/";

    private static Client client = ClientBuilder.newBuilder().build();
    private static WebTarget target = client.target(UriBuilder.fromPath(path));

    private static FacadeActiveMatch facadeActiveMatch = ((ResteasyWebTarget) target).proxy(FacadeActiveMatch.class);

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

        JSONObject oldJSONPlayersData = this.getJSONPlayersCoordinates(
            facadeActiveMatch.getPlayers());

        session.getBasicRemote().sendText(oldJSONPlayersData.toString());

        sessionToPlayer.put(session, id);
        facadeActiveMatch.addPlayer(id);

        Player new_player = facadeActiveMatch.getPlayer(id);

        JSONObject JSON_new_player = this.getJSONPlayersData(
            Map.ofEntries(
              Map.entry(
                new_player.getName(), 
                new int[]{new_player.getX(),
                          new_player.getY()}
                )  
            ),
            "Players");

        broadcast(JSON_new_player);
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        String id = sessionToPlayer.get(session);

        facadeActiveMatch.move(id, message);

        JSONObject json = new JSONObject();

        Player player = facadeActiveMatch.getPlayer(id);

        json.put("data", new JSONObject(
            Map.ofEntries(
              Map.entry(
                player.getName(), 
                new int[]{player.getX(),
                          player.getY()}
                )  
            )
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
            facadeActiveMatch.removePlayer(id);

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
        throwable.printStackTrace();
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

    private JSONObject getJSONPlayersCoordinates(Collection<Player> players) {
        JSONObject JSONPlayersCoordinates = new JSONObject();
            
        Player player;
        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext()) {
            player = iterator.next();

            JSONPlayersCoordinates.put(
                player.getName(),
                new JSONArray(new int[]{
                    player.getX(),
                    player.getY()
                }));
        }

        JSONObject JSONWrapper = new JSONObject();

        JSONWrapper.put("data", JSONPlayersCoordinates);
        JSONWrapper.put("type", "Players");

        return JSONWrapper;
    }
}
