package rs.ac.soas.bank;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@RestController
public class AccountController {
  private final AccountService accounts;

  public AccountController(AccountService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/bank-accounts")
  public List<Api.Account> all(HttpServletRequest req) {
    Access.require(req, Role.ADMIN);
    return accounts.all();
  }

  @GetMapping("/bank-accounts/me")
  public Api.Account own(HttpServletRequest req) {
    Access.require(req, Role.USER);
    return accounts.account(Access.email(req));
  }

  @GetMapping("/bank-accounts/{email}")
  public Api.Account one(@PathVariable String email, HttpServletRequest req) {
    Access.account(req, email);
    return accounts.account(email);
  }

  @PostMapping("/bank-accounts/holdings")
  public Api.Account add(@Valid @RequestBody Api.HoldingInput input, HttpServletRequest req) {
    Access.require(req, Role.ADMIN);
    return accounts.set(input, true);
  }

  @PutMapping("/bank-accounts/holdings")
  public Api.Account update(@Valid @RequestBody Api.HoldingInput input, HttpServletRequest req) {
    Access.require(req, Role.ADMIN);
    return accounts.set(input, false);
  }

  @GetMapping("/internal/bank")
  public Api.Account account(@RequestParam String email) {
    return accounts.account(email);
  }

  @PostMapping("/internal/bank/provision")
  public Api.Account provision(@RequestParam String email) {
    return accounts.provision(email);
  }

  @DeleteMapping("/internal/bank")
  public void delete(@RequestParam String email) {
    accounts.delete(email);
  }

  @PostMapping("/internal/bank/restore")
  public void restore(@RequestBody Api.Account account) {
    accounts.restore(account);
  }

  @PostMapping("/internal/bank/rename")
  public void rename(@RequestBody Api.Rename rename) {
    accounts.rename(rename);
  }

  @PostMapping("/internal/bank/adjust")
  public Api.Account adjust(@RequestBody Api.Adjustment input) {
    return accounts.adjust(input);
  }

  @PostMapping("/internal/bank/swap")
  public Api.Account swap(@RequestBody Api.Swap input) {
    return accounts.swap(input);
  }

  @PostMapping("/internal/bank/undo/{operationId}")
  public void undo(@PathVariable String operationId) {
    accounts.undo(operationId);
  }
}
