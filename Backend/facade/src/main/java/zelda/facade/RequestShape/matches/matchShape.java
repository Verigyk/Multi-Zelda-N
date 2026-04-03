package zelda.facade.RequestShape.matches;

public class matchShape {
    public static record CreateMatchRequest(String title, int maxPlayers){};
    public static record FinishMatchRequest(String winner){}
}
