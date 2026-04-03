package zelda.facade;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import zelda.facade.RequestShape.matches.matchShape;
import zelda.facade.accounts.Account;
import zelda.facade.accounts.AccountRepository;
import zelda.facade.matches.Match;
import zelda.facade.matches.MatchRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private HashMap<String, Match> activeMatches = new HashMap<String,Match>(); 
    @Autowired
    private MatchRepository historyMatches;

    @Autowired
    AccountRepository ar;

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
        activeMatches.put(id, match);
        return match;
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Match> joinMatch(@PathVariable String id) {
        Match match = activeMatches.get(id);

        synchronized (match) {
            if (match.getPlayersCount() < match.getMaxPlayers()) {
                match.setPlayersCount(match.getPlayersCount() + 1);
            }
            return ResponseEntity.ok(match);
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Match> startMatch(@PathVariable String id) {
        Match match = activeMatches.get(id);

        synchronized (match) {
            if ("LOADING".equals(match.getState())) {
                match.setState("RUNNING");
                match.setStartedAt(Instant.now().toString());
            }
            return ResponseEntity.ok(match);
        }
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<Match> finishMatch(Authentication authentication, @PathVariable String id, @RequestBody(required = false) matchShape.FinishMatchRequest request) {
        //Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Match match = activeMatches.get(id);

        synchronized (match) {
            match.setState("FINISHED");
            match.setEndedAt(Instant.now().toString());
            String winner = (request == null || request.winner() == null || request.winner().isBlank())
                ? "Inconnu"
                : request.winner().trim();
            match.setWinner(winner);
        }

        Account account = this.getAccount(authentication.getName());

        activeMatches.remove(id);
        
        historyMatches.save(match);

        account.getMatch_history().add(match);
        this.ar.save(account);

        return ResponseEntity.ok(match);
    }

    @GetMapping("/active")
    public Collection<Match> listActiveMatches() {
        return activeMatches.values();
    }


    // Trouver l'historique d'un compte
    @GetMapping("/history")
    public Collection<Match> listHistoryMatches(Authentication authentication) {
        return this.getAccount(authentication.getName()).getMatch_history();
    }

    private Account getAccount(String pseudo) {
        Optional<Account> a = ar.findById(pseudo);
        if (a.isPresent()) {
            return a.get();
        }

        throw new RuntimeException("Erreur : Compte non existant");
    }

    private int clampMaxPlayers(Integer value) {
        if (value == null) {
            return 4;
        }
        return Math.max(2, Math.min(16, value));
    }
}
