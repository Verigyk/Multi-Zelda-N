package zelda.facade.RequestShape.matches;

public class matchShape {
    public static record CreateMatchRequest(String title, int maxPlayers){};
    public static record FinishMatchRequest(String winner){}
    public static record LobbyMatchResponse(
        String id,
        String title,
        String state,
        int playersCount,
        int maxPlayers,
        String createdAt,
        String startedAt,
        String endedAt,
        String winner,
        boolean joined
    ){}
}
