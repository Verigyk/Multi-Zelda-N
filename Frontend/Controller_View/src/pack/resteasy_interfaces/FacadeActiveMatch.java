package pack.resteasy_interfaces;

import java.util.Collection;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import pack.backendObjects.Player;
import pack.backendObjects.SocketConvention;

@Path("/")
public interface FacadeActiveMatch {
    
    @GET
    @Path("/getPlayers")
    public Collection<Player> getPlayers();

    @POST
    @Path("/act")
    public SocketConvention act(@QueryParam("name") String name, @QueryParam("direction") String direction);

    @POST
    @Path("/addPlayer")
    public void addPlayer(@QueryParam("name") String name);

    @POST
    @Path("/removePlayer")
    public void removePlayer(@QueryParam("name") String name);

    @POST
    @Path("/getPlayer")
    public Player getPlayer(@QueryParam("name") String name);
}
