package zelda.facade;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import zelda.facade.RequestShape.SocketConvention;
import zelda.facade.matches.ModelMatch;
import zelda.facade.players.Player;
import zelda.facade.players.PlayerRepository;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/activeMatch")
public class FacadeActiveMatch {

    @Autowired
    PlayerRepository players;
    
    ModelMatch modelMatch = new ModelMatch();

    final int[][] obstacles = new int[][]{
        new int[]{300, 500, 200, 150},
        new int[]{900, 400, 200, 300}
    };

    final int attack_range = 75;

    @GetMapping("/getPlayers")
    public Collection<Player> getPlayers() {
        return players.findAll();
    }

    @PostMapping("/act")
    public SocketConvention act(@RequestParam String name, @RequestParam String direction) {
        Player player = getPlayer(name);

        SocketConvention socketSent = new SocketConvention();
        Map<String, int[]> players_moved;

        switch (direction) {
            case "HAUT":
                players_moved = translate(player, 0, -Player.walk_step);
                setOrientation(player, "HAUT");
                break;
            case "BAS":
                players_moved = translate(player, 0, Player.walk_step);
                setOrientation(player, "BAS");
                break;
            case "GAUCHE":
                players_moved = translate(player, -Player.walk_step, 0);
                setOrientation(player, "GAUCHE");
                break;
            case "DROITE":
                players_moved = translate(player, Player.walk_step, 0);
                setOrientation(player, "DROITE");
                break;
            case "ATTAQUE":
                players_moved = attaqueBy(player);
                break;
            default:
                throw new RuntimeException("Erreur : Direction inexistante. Impossible de prendre cette direction");

        }

        boolean arePlayers_moved = !players_moved.isEmpty();
        socketSent.setSend(arePlayers_moved);

        if (arePlayers_moved) {
            socketSent.setPlayers(players_moved);
        }

        return socketSent;
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

    private Map<String, int[]> translate(Player translated_player, int x, int y) {
        Map<String, int[]> player_moved = new HashMap<String, int[]>();

        String name = translated_player.getName();
        int x_new = translated_player.getX() + x;
        int y_new = translated_player.getY() + y;

        boolean direction_possible = true;

        Collection<Player> players = getPlayers();
        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext() && direction_possible) {
            Player player = iterator.next();

            if (player.getName() != name) {
                if (Math.abs(player.getX() - x_new) < 2 * Player.demicote &&
                        Math.abs(player.getY() - y_new) < 2 * Player.demicote) {
                    direction_possible = false;
                }
            }
        }

        int i = 0, n = obstacles.length;
        while (i < n && direction_possible) {
            int[] rectangle = obstacles[i];

            if (this.checkCollisions(x_new, y_new, Player.demicote, rectangle)) {
                direction_possible = false;
            }

            i += 1;
        }

        if (direction_possible) {
            translated_player.setX(x_new);
            translated_player.setY(y_new);

            this.players.save(translated_player);

            player_moved.put(translated_player.getName(), 
                    new int[]{translated_player.getX(),
                            translated_player.getY()
                    });
        }

        return player_moved;
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

    private void setOrientation(Player player, String new_orientation) {
        player.setOrientation(new_orientation);

        players.save(player);
    }

    private Map<String, int[]> attaqueBy(Player player) {
        Map<String, int[]> playersAttacked = new HashMap<String, int[]>();

        int x_attack = player.getX();
        int y_attack = player.getY();

        switch (player.getOrientation()) {
            case "HAUT":
                y_attack -= attack_range;
                break;
            case "GAUCHE":
                x_attack -= attack_range;
                break;
            case "DROITE":
                x_attack += attack_range;
                break;
            case "BAS":
                y_attack += attack_range;
                break;
        }

        Collection<Player> collection_players = getPlayers();

        Iterator<Player> iterator = collection_players.iterator();

        while (iterator.hasNext()) {
            Player other_player = iterator.next();

            if (!other_player.getName().equals(player.getName())) {
                if (checkCollisions(other_player.getX(), other_player.getY(), Player.demicote, new int[]{x_attack, y_attack, Player.demicote, Player.demicote})) {
                    other_player.reset();
                    players.save(other_player);
                    playersAttacked.put(other_player.getName(), new int[]{other_player.getX(), other_player.getY()});
                }
            }
        }

        return playersAttacked;
    }
}
