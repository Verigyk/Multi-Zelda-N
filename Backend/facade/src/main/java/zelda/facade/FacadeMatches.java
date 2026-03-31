package zelda.facade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/matches")
public class FacadeMatches {
    private static final int MAX_HISTORY = 100;
    private final Map<String, MatchModel> activeMatches = new ConcurrentHashMap<>();
    private final Deque<MatchModel> historyMatches = new ConcurrentLinkedDeque<>();

    @PostMapping("/create")
    public MatchView createMatch(@RequestBody(required = false) CreateMatchRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        int maxPlayers = clampMaxPlayers(request == null ? null : request.getMaxPlayers());
        String title = (request == null || request.getTitle() == null || request.getTitle().isBlank())
            ? "Partie " + id
            : request.getTitle().trim();

        MatchModel match = new MatchModel();
        match.setId(id);
        match.setTitle(title);
        match.setState("LOADING");
        match.setPlayersCount(1);
        match.setMaxPlayers(maxPlayers);
        match.setCreatedAt(Instant.now().toString());
        match.setStartedAt(null);
        match.setEndedAt(null);
        match.setWinner(null);

        activeMatches.put(id, match);
        return toView(match);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<MatchView> joinMatch(@PathVariable String id) {
        MatchModel match = activeMatches.get(id);
        if (match == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        synchronized (match) {
            if (match.getPlayersCount() < match.getMaxPlayers()) {
                match.setPlayersCount(match.getPlayersCount() + 1);
            }
            return ResponseEntity.ok(toView(match));
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<MatchView> startMatch(@PathVariable String id) {
        MatchModel match = activeMatches.get(id);
        if (match == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        synchronized (match) {
            if ("LOADING".equals(match.getState())) {
                match.setState("RUNNING");
                match.setStartedAt(Instant.now().toString());
            }
            return ResponseEntity.ok(toView(match));
        }
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<MatchView> finishMatch(@PathVariable String id, @RequestBody(required = false) FinishMatchRequest request) {
        MatchModel match = activeMatches.remove(id);
        if (match == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        synchronized (match) {
            match.setState("FINISHED");
            match.setEndedAt(Instant.now().toString());
            String winner = (request == null || request.getWinner() == null || request.getWinner().isBlank())
                ? "Inconnu"
                : request.getWinner().trim();
            match.setWinner(winner);
        }

        historyMatches.addFirst(match);
        while (historyMatches.size() > MAX_HISTORY) {
            historyMatches.pollLast();
        }

        return ResponseEntity.ok(toView(match));
    }

    @GetMapping("/active")
    public List<MatchView> listActiveMatches() {
        return activeMatches.values().stream()
            .sorted(Comparator.comparing(MatchModel::getCreatedAt).reversed())
            .map(this::toView)
            .toList();
    }

    @GetMapping("/history")
    public List<MatchView> listHistoryMatches() {
        List<MatchView> history = new ArrayList<>();
        for (MatchModel match : historyMatches) {
            history.add(toView(match));
        }
        return history;
    }

    @GetMapping("/ping")
    public ApiInfoView ping(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String apiBase = contextPath + "/matches";
        return new ApiInfoView("ok", contextPath, apiBase);
    }

    private int clampMaxPlayers(Integer value) {
        if (value == null) {
            return 4;
        }
        return Math.max(2, Math.min(16, value));
    }

    private MatchView toView(MatchModel match) {
        return new MatchView(
            match.getId(),
            match.getTitle(),
            match.getState(),
            match.getPlayersCount(),
            match.getMaxPlayers(),
            match.getCreatedAt(),
            match.getStartedAt(),
            match.getEndedAt(),
            match.getWinner()
        );
    }

    public static class CreateMatchRequest {
        private String title;
        private Integer maxPlayers;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getMaxPlayers() {
            return maxPlayers;
        }

        public void setMaxPlayers(Integer maxPlayers) {
            this.maxPlayers = maxPlayers;
        }
    }

    public static class FinishMatchRequest {
        private String winner;

        public String getWinner() {
            return winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }
    }

    public static class MatchView {
        private final String id;
        private final String title;
        private final String state;
        private final int playersCount;
        private final int maxPlayers;
        private final String createdAt;
        private final String startedAt;
        private final String endedAt;
        private final String winner;

        public MatchView(
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
    }

    public static class ApiInfoView {
        private final String status;
        private final String contextPath;
        private final String apiBase;

        public ApiInfoView(String status, String contextPath, String apiBase) {
            this.status = status;
            this.contextPath = contextPath;
            this.apiBase = apiBase;
        }

        public String getStatus() {
            return status;
        }

        public String getContextPath() {
            return contextPath;
        }

        public String getApiBase() {
            return apiBase;
        }
    }

    private static class MatchModel {
        private String id;
        private String title;
        private String state;
        private int playersCount;
        private int maxPlayers;
        private String createdAt;
        private String startedAt;
        private String endedAt;
        private String winner;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public int getPlayersCount() {
            return playersCount;
        }

        public void setPlayersCount(int playersCount) {
            this.playersCount = playersCount;
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getEndedAt() {
            return endedAt;
        }

        public void setEndedAt(String endedAt) {
            this.endedAt = endedAt;
        }

        public String getWinner() {
            return winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }
    }
}
