package zelda.facade;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import zelda.facade.accounts.Account;
import zelda.facade.accounts.AccountRepository;
import zelda.facade.matches.Match;

import java.util.Collection;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/accounts")
public class FacadeAccounts {
    @Autowired
    AccountRepository ar;

    // Créer un compte
    @PostMapping("/addAccount")
    public ResponseEntity<String> addAccount(@RequestBody Account a) {
        if (ar.existsById(a.getPseudo())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce pseudo est déjà pris.");
        }
        ar.save(a);
        return ResponseEntity.ok("Compte créé avec succès.");
    }

    // Se connecter
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Account a) {
        Optional<Account> found = ar.findById(a.getPseudo());
        if (found.isPresent() && found.get().getPassword().equals(a.getPassword())) {
            return ResponseEntity.ok("Connexion réussie.");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Pseudo ou mot de passe incorrect.");
    }

    // Lister tous les comptes (utile pour déboguer)
    @GetMapping("/listAccounts")
    public Collection<Account> listAccounts() {
        return ar.findAll();
    }

    // Trouver l'historique d'un compte
    @GetMapping("/history")
    public Collection<Match> listHistoryMatches(@RequestParam(name = "pseudo") String pseudo) {
        return this.getAccount(pseudo).getMatch_history();
    }

    private Account getAccount(String pseudo) {
        Optional<Account> a = ar.findById(pseudo);
        if (a.isPresent()) {
            return a.get();
        }

        throw new RuntimeException("Erreur : Compte non existant");
    }
}
