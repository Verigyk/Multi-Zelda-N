package zelda.facade;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import zelda.facade.accounts.Account;
import zelda.facade.accounts.AccountRepository;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/accounts")
public class FacadeAccounts {
    @Autowired
    AccountRepository ar;

    // Lister tous les comptes (utile pour déboguer)
    @GetMapping("/listAccounts")
    public Collection<Account> listAccounts() {
        return ar.findAll();
    }
}
