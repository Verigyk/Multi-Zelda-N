package pack.backendObjects;

import java.util.Collection;

public class Account {
    String pseudo;
    String password;

    Collection<Match> match_history;

    Player player;

    public Account() {}

    public Account(String pseudo, String password) {
        this.pseudo = pseudo;
        this.password = password;
    }

    public String getPseudo() {
        return pseudo;
    }

    public String getPassword() {
        return password;
    }
    
    public Collection<Match> getMatch_history() {
        return match_history;
    }
}

