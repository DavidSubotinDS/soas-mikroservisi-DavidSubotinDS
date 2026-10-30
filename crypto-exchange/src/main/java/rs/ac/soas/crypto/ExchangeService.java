package rs.ac.soas.crypto;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@Service
public class ExchangeService {
  private final RateProvider provider;

  public ExchangeService(RateProvider provider) {
    this.provider = provider;
  }

  public Api.Rate rate(String rawFrom, String rawTo) {
    Set<String> allowed = new HashSet<>(Assets.CRYPTO);
    allowed.addAll(Set.of("EUR", "USD"));
    String from = Checks.asset(rawFrom, allowed, "crypto/settlement asset"),
        to = Checks.asset(rawTo, allowed, "crypto/settlement asset");
    return new Api.Rate(
        from,
        to,
        from.equals(to) ? BigDecimal.ONE : Checks.rate(provider.rate(from, to)),
        "Coinbase public rates");
  }
}
