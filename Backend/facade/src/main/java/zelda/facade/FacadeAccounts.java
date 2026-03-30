package zelda.facade;

import org.springframework.web.bind.annotation.RestController;

import zelda.facade.accounts.Account;
import zelda.facade.accounts.AccountRepository;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/accounts")
public class FacadeAccounts {
    @Autowired
    AccountRepository ar;

    @PostMapping("/addAccount")
    public void addAccount(Account a) {
        ar.save(a);
    }

    @GetMapping("/listAccounts")
    public Collection<Account> listAccounts() {
        return ar.findAll();
    }
    

}
