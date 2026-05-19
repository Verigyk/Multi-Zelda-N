package zelda.facade.RequestShape.matches;

public class matchShape {
    public static record CreateMatchRequest(String title, int maxPlayers, String mapName){};
    public static record FinishMatchRequest(String winner){}
    public static record MapOptionResponse(String name, int maxPlayers){}
    public static record MapDefinitionResponse(String name, int width, int height, int gemCount, int[][] obstacles, int[][] startPositions){}
    public static record LobbyMatchResponse(
        String id,
        String title,
        String state,
        int playersCount,
        int maxPlayers,
        String mapName,
        String createdAt,
        String startedAt,
        String endedAt,
        String winner,
        String createdBy,
        boolean joined
    ){}
}
