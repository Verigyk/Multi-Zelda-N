package zelda.facade;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import zelda.facade.accounts.AccountRepository;
import zelda.facade.matches.ModelMatch;
import zelda.facade.players.Player;
import zelda.facade.players.PlayerRepository;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/activeMatch")
public class FacadeActiveMatch {

    private final AccountRepository accountRepository;

    @Autowired
    PlayerRepository players;
    
    ModelMatch modelMatch = new ModelMatch();

    final int[][] obstacles = new int[][]{
        new int[]{300, 500, 200, 150},
        new int[]{900, 400, 200, 300}
    };

    FacadeActiveMatch(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/getPlayers")
    public Collection<Player> getPlayers() {
        return players.findAll();
    }

    @PostMapping("/move")
    public void move(@RequestParam String name, @RequestParam String direction) {
        Player player = getPlayer(name);

        switch (direction) {
            case "HAUT":
                translate(player, 0, -Player.walk_step);
                break;
            case "BAS":
                translate(player, 0, Player.walk_step);
                break;
            case "GAUCHE":
                translate(player, -Player.walk_step, 0);
                break;
            case "DROITE":
                translate(player, Player.walk_step, 0);
                break;
            default:
                throw new RuntimeException("Erreur : Direction inexistante. Impossible de prendre cette direction");
        }
    }

    @PostMapping("/addPlayer")
    public void addPlayer(@RequestParam String name) {
        /*
        if (this.players.existsById(name)) {
            throw new RuntimeException("Erreur : Ajout de joueur impossible. Joueur déjà présent dans le match");
        }

        if (!this.accountRepository.existsById(name)) {
            throw new RuntimeException("Erreur : Ajout de joueur impossible. Compte non existant");
        }

        Account account = this.accountRepository.findById(name).get();*/

        this.players.save(
            new Player(name, modelMatch.getCoordinate_x(), 50)
        );

        this.modelMatch.setCoordinate_x(modelMatch.getCoordinate_x() + ModelMatch.spawn_step);
        
    }

    @PostMapping("/removePlayer")
    public void removePlayer(@RequestParam String name) {
        if (this.players.existsById(name)) {
            this.players.deleteById(name);
            this.modelMatch.setCoordinate_x(modelMatch.getCoordinate_x() - ModelMatch.spawn_step);
        } else {
            throw new RuntimeException("Erreur : Suppression impossible. Le joueur n'est pas présent dans le match.");
        }
    }

    private void translate(Player translated_player, int x, int y) {
        String name = translated_player.getName();
        int x_new = translated_player.getX() + x;
        int y_new = translated_player.getY() + y;

        boolean direction_possible = true;

        Collection<Player> players = getPlayers();
        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext() && direction_possible) {
            Player player = iterator.next();

            if (player.getName() != name) {
                if (Math.abs(player.getX() - x_new) < 2 * Player.cote &&
                        Math.abs(player.getY() - y_new) < 2 * Player.cote) {
                    direction_possible = false;
                }
            }
        }

        int i = 0, n = obstacles.length;
        while (i < n && direction_possible) {
            int[] rectangle = obstacles[i];

            if (this.checkCollisions(x_new, y_new, Player.cote, rectangle)) {
                direction_possible = false;
            }

            i += 1;
        }

        if (direction_possible) {
            translated_player.setX(x_new);
            translated_player.setY(y_new);

            this.players.save(translated_player);
        }
    }

    @PostMapping("/getPlayer")
    public Player getPlayer(@RequestParam String name) {
        Optional<Player> p = players.findById(name);

        if (!p.isPresent()) {
            throw new RuntimeException("Erreur : Impossible d'obtenir des informations du joueur. Joueur non présent");
        }

        return p.get();
    }

    // Rectangle [x, y, longueur / 2, largeur / 2]
    private boolean checkCollisions(int x, int y, int cote, int[] rectangle) {
        return (Math.abs(rectangle[0] - x) < rectangle[2] + cote &&
                Math.abs(rectangle[1] - y) < rectangle[3] + cote);
    }

}
