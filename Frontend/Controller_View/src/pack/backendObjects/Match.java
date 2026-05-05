package pack.backendObjects;

public class Match {
    private String id;
    private String title;
    private String state;
    private int playersCount;
    private int maxPlayers;
    private String createdAt;
    private String startedAt;
    private String endedAt;
    private String winner;

    public Match() {

    }

    public Match(
        String id,
        String title,
        String state,
        int maxPlayers,
        String createdAt,
        String startedAt,
        String endedAt,
        String winner
    ) {
        this.id = id;
        this.title = title;
        this.state = state;
        this.maxPlayers = maxPlayers;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.winner = winner;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getState() {
        return state;
    }

    public int getPlayersCount() {
        return playersCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public String getWinner() {
        return winner;
    }
}
