package rs.ac.soas.fiat;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@Service
public class ExchangeService {
  private final RateProvider provider;
  private List<String> currencies = List.of();
  private long expiresAt;

  public ExchangeService(RateProvider provider) {
    this.provider = provider;
  }

  public Api.Rate rate(String rawFrom, String rawTo) {
    String from = Checks.fiatCode(rawFrom), to = Checks.fiatCode(rawTo);
    var available = currencies();
    Checks.asset(from, Set.copyOf(available), "fiat currency");
    Checks.asset(to, Set.copyOf(available), "fiat currency");
    return new Api.Rate(
        from,
        to,
        from.equals(to) ? BigDecimal.ONE : Checks.rate(provider.rate(from, to)),
        "Frankfurter v2");
  }

  public synchronized List<String> currencies() {
    if (System.nanoTime() - expiresAt >= 0 || currencies.isEmpty()) {
      var available = provider.currencies();
      if (available == null || available.isEmpty())
        throw BusinessException.unavailable("Fiat currency catalog is unavailable");
      currencies = available.stream().distinct().sorted().toList();
      expiresAt = System.nanoTime() + java.time.Duration.ofMinutes(5).toNanos();
    }
    return currencies;
  }
}
