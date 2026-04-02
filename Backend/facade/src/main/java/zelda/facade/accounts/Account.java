package zelda.facade.accounts;

import java.util.Collection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import zelda.facade.matches.Match;

@Entity
public class Account {
    @Id
    String pseudo;
    String password;

    @ManyToMany
    Collection<Match> match_history;


    public Account() {}

    public Account(String pseudo, String password) {
        this.pseudo = pseudo;
        this.password = password;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
    public Collection<Match> getMatch_history() {
        return match_history;
    }

    public void setMatch_history(Collection<Match> match_history) {
        this.match_history = match_history;
    }
}
