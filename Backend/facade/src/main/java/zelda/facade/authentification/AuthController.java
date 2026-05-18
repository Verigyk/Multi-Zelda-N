package zelda.facade.authentification;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import zelda.facade.accounts.Account;
import zelda.facade.accounts.AccountRepository;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    @Autowired
    AccountRepository ar;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        Optional<Account> a = ar.findById(request.username());

        if (!a.isPresent()) {
            return ResponseEntity.status(401).body("Invalid username");
        }

        Account account = a.get();

        if (!account.getPassword().equals(request.password())) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        String token = jwtUtil.generateToken(request.username());

        addTokenCookie(response, token, (int) (JwtUtil.EXPIRATION_MS / 1000));

        return ResponseEntity.ok("Login successsful");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        addTokenCookie(response, "", 0);

        return ResponseEntity.ok("Logout successful");
    }

    // Créer un compte
    @PostMapping("/addAccount")
    public ResponseEntity<String> addAccount(@RequestBody Account a) {
        if (ar.existsById(a.getPseudo())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce pseudo est déjà pris.");
        }
        ar.save(a);
        return ResponseEntity.ok("Compte créé avec succès.");
    }

    private void addTokenCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        // The project is usually tested over plain HTTP on a LAN IP, so Secure must stay false here.
        response.addHeader(
            "Set-Cookie",
            "Token=" + value + "; Max-Age=" + maxAgeSeconds + "; Path=/; HttpOnly; SameSite=Lax"
        );
    }
}

record LoginRequest(String username, String password) {}
record JwtResponse(String token) {}
