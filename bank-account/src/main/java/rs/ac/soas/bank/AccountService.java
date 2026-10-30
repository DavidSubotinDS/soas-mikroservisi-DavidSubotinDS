package rs.ac.soas.bank;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@Service
@Transactional
public class AccountService {
  private final HoldingRepository holdings;
  private final LedgerRepository ledger;
  private final UsersClient users;

  public AccountService(HoldingRepository holdings, LedgerRepository ledger, UsersClient users) {
    this.holdings = holdings;
    this.ledger = ledger;
    this.users = users;
  }

  private String currency(String raw) {
    return Checks.fiat(raw);
  }

  private void requireUser(String email) {
    if (users.lookup(email).role() != Role.USER)
      throw BusinessException.bad("Only USER accounts may own holdings");
  }

  public Api.Account account(String rawEmail) {
    String email = Assets.email(rawEmail);
    return new Api.Account(
        email,
        holdings.findByEmailOrderByCurrency(email).stream()
            .map(h -> new Api.Holding(h.id, h.currency, h.amount))
            .toList());
  }

  public List<Api.Account> all() {
    return holdings.findAllByOrderByEmailAscCurrencyAsc().stream()
        .map(h -> h.email)
        .distinct()
        .map(this::account)
        .toList();
  }

  public synchronized Api.Account provision(String rawEmail) {
    String email = Assets.email(rawEmail);
    requireUser(email);
    if (holdings.findByEmailAndCurrency(email, "EUR").isEmpty())
      holdings.saveAndFlush(new Holding(email, "EUR", Assets.amount(BigDecimal.ZERO)));
    return account(email);
  }

  public synchronized Api.Account set(Api.HoldingInput input, boolean create) {
    String email = Assets.email(input.email()), code = currency(input.currency());
    requireUser(email);
    BigDecimal amount = Checks.nonnegative(input.amount());
    Optional<Holding> existing = holdings.findByEmailAndCurrency(email, code);
    if (create && existing.isPresent())
      throw BusinessException.conflict("This currency holding already exists");
    if (!create && existing.isEmpty())
      throw BusinessException.missing("Currency holding does not exist");
    Holding h = existing.orElseGet(() -> new Holding(email, code, amount));
    h.amount = amount;
    holdings.saveAndFlush(h);
    return account(email);
  }

  public synchronized void delete(String email) {
    holdings.deleteByEmail(Assets.email(email));
  }

  public synchronized void restore(Api.Account account) {
    holdings.deleteByEmail(account.email());
    holdings.flush();
    for (Api.Holding h : account.holdings())
      holdings.save(
          new Holding(account.email(), currency(h.currency()), Checks.nonnegative(h.amount())));
    holdings.flush();
  }

  public synchronized void rename(Api.Rename rename) {
    String oldEmail = Assets.email(rename.oldEmail()), newEmail = Assets.email(rename.newEmail());
    if (!account(newEmail).holdings().isEmpty())
      throw BusinessException.conflict("New email already owns holdings");
    for (Holding h : holdings.findByEmailOrderByCurrency(oldEmail)) {
      h.email = newEmail;
      holdings.save(h);
    }
    holdings.flush();
  }

  public synchronized Api.Account adjust(Api.Adjustment input) {
    String code = currency(input.currency());
    if (input.delta() == null || input.delta().signum() == 0)
      throw BusinessException.bad("Adjustment must be nonzero");
    Checks.nonnegative(input.delta().abs());
    return apply(input.email(), Map.of(code, input.delta()), input.operationId());
  }

  public synchronized Api.Account swap(Api.Swap input) {
    String from = currency(input.from()), to = currency(input.to());
    BigDecimal q = Checks.positive(input.quantity()), received = Checks.positive(input.received());
    Map<String, BigDecimal> deltas = new TreeMap<>();
    deltas.merge(from, q.negate(), BigDecimal::add);
    deltas.merge(to, received, BigDecimal::add);
    return apply(input.email(), deltas, input.operationId());
  }

  private Api.Account apply(String rawEmail, Map<String, BigDecimal> deltas, String operationId) {
    String email = Assets.email(rawEmail);
    if (operationId == null || !operationId.matches("[a-zA-Z0-9:._-]{1,120}"))
      throw BusinessException.bad("Valid operationId required");
    String encoded =
        deltas.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue().toPlainString())
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    Optional<LedgerEntry> previous = ledger.findById(operationId);
    if (previous.isPresent()) {
      LedgerEntry entry = previous.get();
      if (!entry.email.equals(email) || !entry.deltas.equals(encoded) || entry.undone)
        throw BusinessException.conflict("Operation ID already used with another operation");
      return account(email);
    }
    requireUser(email);
    mutate(email, deltas);
    ledger.saveAndFlush(new LedgerEntry(operationId, email, encoded));
    return account(email);
  }

  private void mutate(String email, Map<String, BigDecimal> deltas) {
    List<Holding> changes = new ArrayList<>();
    for (var item : deltas.entrySet()) {
      Optional<Holding> existing = holdings.findByEmailAndCurrency(email, item.getKey());
      if (existing.isEmpty() && item.getValue().signum() < 0)
        throw BusinessException.missing("Missing bank holding: " + item.getKey());
      Holding h = existing.orElseGet(() -> new Holding(email, item.getKey(), BigDecimal.ZERO));
      BigDecimal amount = Assets.amount(h.amount.add(item.getValue()));
      if (amount.signum() < 0)
        throw BusinessException.bad("Insufficient bank balance in " + item.getKey());
      h.amount = Checks.nonnegative(amount);
      changes.add(h);
    }
    holdings.saveAllAndFlush(changes);
  }

  public synchronized void undo(String operationId) {
    Optional<LedgerEntry> found = ledger.findById(operationId);
    if (found.isEmpty() || found.get().undone) return;
    LedgerEntry entry = found.get();
    Map<String, BigDecimal> reverse = new TreeMap<>();
    for (String part : entry.deltas.split(",")) {
      String[] kv = part.split("=");
      reverse.put(kv[0], new BigDecimal(kv[1]).negate());
    }
    mutate(entry.email, reverse);
    entry.undone = true;
    ledger.saveAndFlush(entry);
  }
}
