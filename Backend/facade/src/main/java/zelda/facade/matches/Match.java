package zelda.facade.matches;

import java.util.Collection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import zelda.facade.accounts.Account;

@Entity
public class Match {
    @Id
    private String id;
    private String title;
    private String state;
    private int playersCount;
    private int maxPlayers;
    private String createdAt;
    private String startedAt;
    private String endedAt;
    private String winner;

    @ManyToMany(mappedBy = "match_history")
    private Collection<Account> participants;

    public Match(
        String id,
        String title,
        String state,
        int playersCount,
        int maxPlayers,
        String createdAt,
        String startedAt,
        String endedAt,
        String winner
    ) {
        this.id = id;
        this.title = title;
        this.state = state;
        this.playersCount = playersCount;
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

    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setPlayersCount(int playersCount) {
        this.playersCount = playersCount;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }
}
