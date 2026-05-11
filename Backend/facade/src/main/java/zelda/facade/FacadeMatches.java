package zelda.facade;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import zelda.facade.RequestShape.matches.matchShape;
import zelda.facade.accounts.Account;
import zelda.facade.accounts.AccountRepository;
import zelda.facade.matches.Match;
import zelda.facade.matches.MatchRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    private HashMap<String, Match> activeMatches = new HashMap<String,Match>(); 
    private HashMap<String, HashSet<String>> activeMatchesPlayersIds = new HashMap<String,HashSet<String>>(); 

    @Autowired
    private MatchRepository historyMatches;

    @Autowired
    AccountRepository ar;

    @PostMapping("/create")
    public Match createMatch(Authentication authentication, @RequestBody(required = false) matchShape.CreateMatchRequest request) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        int maxPlayers = clampMaxPlayers(request == null ? null : request.maxPlayers());
        String title = (request == null || request.title() == null || request.title().isBlank())
            ? "Partie " + id
            : request.title().trim();

        Match match = new Match(id,
                                title,
                                "LOADING",
                                maxPlayers,
                                Instant.now().toString(),
                                null,
                                null,
                                null
        );
        HashSet<String> pseudos = new HashSet<String>();
        match.setPlayersCount(pseudos.size());

        activeMatches.put(id, match);
        activeMatchesPlayersIds.put(id, pseudos);
        return match;
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Match> joinMatch(Authentication authentication, @PathVariable String id) {
        Match match = activeMatches.get(id);
        if (match == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!"LOADING".equals(match.getState())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(match);
        }

        Account account = this.getAccount(authentication.getName());

        HashSet<String> activeMatchPlayersIds = activeMatchesPlayersIds.get(id);

        synchronized (match) {
            if (match.getPlayersCount() < match.getMaxPlayers() && !activeMatchesPlayersIds.get(id).contains(account.getPseudo())) {
                activeMatchPlayersIds.add(account.getPseudo());
                match.setPlayersCount(activeMatchPlayersIds.size());
            }
            return ResponseEntity.ok(match);
        }
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Match> leaveMatch(Authentication authentication, @PathVariable String id) {
        Match match = activeMatches.get(id);
        if (match == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Account account = this.getAccount(authentication.getName());
        HashSet<String> activeMatchPlayersIds = activeMatchesPlayersIds.get(id);

        synchronized (match) {
            if (activeMatchPlayersIds != null && activeMatchPlayersIds.remove(account.getPseudo())) {
                match.setPlayersCount(activeMatchPlayersIds.size());
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
        Match match = activeMatches.get(id);

        synchronized (match) {
            match.setState("FINISHED");
            match.setEndedAt(Instant.now().toString());
            String winner = (request == null || request.winner() == null || request.winner().isBlank())
                ? "Inconnu"
                : request.winner().trim();
            match.setWinner(winner);
        }

        activeMatches.remove(id);
        
        historyMatches.save(match);

        for (String pseudo : activeMatchesPlayersIds.get(id)) {
            Account account = this.getAccount(pseudo);
            account.getMatch_history().add(match);
            this.ar.save(account);
        }

        activeMatchesPlayersIds.remove(id);

        return ResponseEntity.ok(match);
    }

    @GetMapping("/active")
    public Collection<matchShape.LobbyMatchResponse> listActiveMatches(Authentication authentication) {
        String pseudo = authentication.getName();
        return activeMatches.values()
            .stream()
            .map(match -> toLobbyMatchResponse(match, pseudo))
            .collect(Collectors.toList());
    }


    // Trouver l'historique d'un compte
    @GetMapping("/history")
    public Collection<matchShape.LobbyMatchResponse> listHistoryMatches(Authentication authentication) {
        return this.getAccount(authentication.getName()).getMatch_history()
            .stream()
            .map(match -> toHistoryMatchResponse(match))
            .collect(Collectors.toList());
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

    private matchShape.LobbyMatchResponse toLobbyMatchResponse(Match match, String pseudo) {
        HashSet<String> players = activeMatchesPlayersIds.get(match.getId());
        boolean joined = players != null && players.contains(pseudo);
        return toLobbyMatchResponse(match, joined);
    }

    private matchShape.LobbyMatchResponse toHistoryMatchResponse(Match match) {
        return toLobbyMatchResponse(match, true);
    }

    private matchShape.LobbyMatchResponse toLobbyMatchResponse(Match match, boolean joined) {
        return new matchShape.LobbyMatchResponse(
            match.getId(),
            match.getTitle(),
            match.getState(),
            match.getPlayersCount(),
            match.getMaxPlayers(),
            match.getCreatedAt(),
            match.getStartedAt(),
            match.getEndedAt(),
            match.getWinner(),
            joined
        );
    }
}
