package zelda.facade;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import zelda.facade.RequestShape.matches.matchShape;
import zelda.facade.matches.Match;
import zelda.facade.matches.MatchRepository;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private MatchRepository activeMatches; 

    @PostMapping("/create")
    public Match createMatch(@RequestBody(required = false) matchShape.CreateMatchRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        int maxPlayers = clampMaxPlayers(request == null ? null : request.maxPlayers());
        String title = (request == null || request.title() == null || request.title().isBlank())
            ? "Partie " + id
            : request.title().trim();

        Match match = new Match(id,
                                title,
                                "LOADING",
                                1,
                                maxPlayers,
                                Instant.now().toString(),
                                null,
                                null,
                                null
        );

        activeMatches.save(match);
        return match;
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Match> joinMatch(@PathVariable String id) {
        Optional<Match> m = activeMatches.findById(id);
        if (!m.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Match match = m.get();

        synchronized (match) {
            if (match.getPlayersCount() < match.getMaxPlayers()) {
                match.setPlayersCount(match.getPlayersCount() + 1);
            }
            return ResponseEntity.ok(match);
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Match> startMatch(@PathVariable String id) {
        Optional<Match> m = activeMatches.findById(id);
        if (!m.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Match match = m.get();

        synchronized (match) {
            if ("LOADING".equals(match.getState())) {
                match.setState("RUNNING");
                match.setStartedAt(Instant.now().toString());
            }
            return ResponseEntity.ok(match);
        }
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<Match> finishMatch(@PathVariable String id, @RequestBody(required = false) matchShape.FinishMatchRequest request) {
        Optional<Match> m = activeMatches.findById(id);
        if (!m.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Match match = m.get();

        synchronized (match) {
            match.setState("FINISHED");
            match.setEndedAt(Instant.now().toString());
            String winner = (request == null || request.winner() == null || request.winner().isBlank())
                ? "Inconnu"
                : request.winner().trim();
            match.setWinner(winner);
        }

        activeMatches.deleteById(id);

        return ResponseEntity.ok(match);
    }

    @GetMapping("/active")
    public List<Match> listActiveMatches() {
        return activeMatches.findAll();
    }

    private int clampMaxPlayers(Integer value) {
        if (value == null) {
            return 4;
        }
        return Math.max(2, Math.min(16, value));
    }
}
