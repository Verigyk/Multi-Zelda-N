//Décris le déroulé d'un match

package pack;

import static java.util.Map.entry;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ModelMatch {
    private ConcurrentHashMap<String, int[]> coordinatesPlayers;

    private ConcurrentLinkedQueue<int[]> obstacles;

    private int coordinate_x = 50;

    private final int spawn_step = 100;
    private final int walk_step = 5;

    private final int cote = 25;

    public ModelMatch() {
        this.coordinatesPlayers = new ConcurrentHashMap<String, int[]>(); 
        this.obstacles = new ConcurrentLinkedQueue<int[]>();

        this.obstacles.add(new int[]{300, 500, 200, 150});
        this.obstacles.add(new int[]{900, 400, 200, 300});
    }

    public ConcurrentHashMap<String, int[]> getCoordinatesPlayers() {
        return this.coordinatesPlayers;
    } 

    public Map<String, int[]> getInformationPlayer(String name) {
        return Map.ofEntries(
            entry(name, this.coordinatesPlayers.get(name))
        );
    }

    public void move(String name, String direction) {
        switch (direction) {
            case "HAUT":
                translate(name, 0, -walk_step);
                break;
            case "BAS":
                translate(name, 0, walk_step);
                break;
            case "GAUCHE":
                translate(name, -walk_step, 0);
                break;
            case "DROITE":
                translate(name, walk_step, 0);
                break;
            default:
                throw new RuntimeException("Erreur : Direction inexistante. Impossible de prendre cette direction");
        }
    }

    public void addPlayer(String name) {
        if (!this.coordinatesPlayers.containsKey(name)) {
            this.coordinatesPlayers.put(name, new int[]{
                coordinate_x,
                50,
            });
            coordinate_x += spawn_step;
        } else {
            throw new RuntimeException("Erreur : Ajout de joueur impossible. Joueur déjà présent dans le match");
        }
    }

    public void removePlayer(String name) {
        if (this.coordinatesPlayers.containsKey(name)) {
            this.coordinatesPlayers.remove(name);
            this.coordinate_x -= spawn_step;
        } else {
            throw new RuntimeException("Erreur : Suppression impossible. Le joueur n'est pas présent dans le match.");
        }
    }

    private void translate(String name, int x, int y) {
        if (this.coordinatesPlayers.containsKey(name)) {
            int[] coords_current_player = this.coordinatesPlayers.get(name);

            int x_new = coords_current_player[0] + x;
            int y_new = coords_current_player[1] + y;

            boolean direction_possible = true;

            Iterator<Map.Entry<String, int[]>> iterator = this.coordinatesPlayers.entrySet().iterator();
            while (iterator.hasNext() && direction_possible) {
                Map.Entry<String, int[]> element = iterator.next();
                
                if (element.getKey() != name) {
                    int[] coords_other_player = element.getValue();
                    if (Math.abs(coords_other_player[0] - x_new) < 2 * cote &&
                        Math.abs(coords_other_player[1] - y_new) < 2 * cote) {
                        direction_possible = false;
                    }
                }
            }

            Iterator<int[]> iterator_array = this.obstacles.iterator();
            while (iterator_array.hasNext() && direction_possible) {
                int[] rectangle = iterator_array.next();
                
                if (this.checkCollisions(x_new, y_new, cote, rectangle)) {
                    direction_possible = false;
                }
            }

            if (direction_possible) {
                coords_current_player[0] = x_new;
                coords_current_player[1] = y_new;

                this.coordinatesPlayers.put(name, coords_current_player);
            }

        } else {
            throw new RuntimeException("Erreur : Translation de position impossible. Le joueur n'est pas présent dans le match.");
        }
    }

    //Rectangle [x, y, longueur / 2, largeur / 2]
    private boolean checkCollisions(int x, int y, int cote, int[] rectangle) {
        return (Math.abs(rectangle[0] - x) < rectangle[2] + cote &&
            Math.abs(rectangle[1] - y) < rectangle[3] + cote);
    }
    
}
