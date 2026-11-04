package rs.ac.soas.util;

import java.time.Duration;
import java.util.Set;
import rs.ac.soas.contract.FiatClient;

/** Validates fiat holdings against the exchange service through Feign, never a local allowlist. */
public class FiatCurrencies {
  private final FiatClient exchange;
  private Set<String> currencies = Set.of();
  private long expiresAt;

  public FiatCurrencies(FiatClient exchange) {
    this.exchange = exchange;
  }

  public synchronized String require(String raw) {
    String code = Checks.fiatCode(raw);
    if (System.nanoTime() - expiresAt >= 0 || currencies.isEmpty()) {
      var available = exchange.currencies();
      if (available == null
          || available.isEmpty()
          || available.stream().anyMatch(value -> value == null || !value.matches("[A-Z]{3}"))) {
        throw BusinessException.unavailable("Fiat currency catalog is unavailable or malformed");
      }
      currencies = Set.copyOf(available);
      expiresAt = System.nanoTime() + Duration.ofMinutes(1).toNanos();
    }
    if (!currencies.contains(code))
      throw BusinessException.bad("Unsupported fiat currency: " + code);
    return code;
  }
}
