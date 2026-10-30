package rs.ac.soas.fiat;

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
    String from = Checks.fiat(rawFrom), to = Checks.fiat(rawTo);
    return new Api.Rate(
        from,
        to,
        from.equals(to) ? BigDecimal.ONE : Checks.rate(provider.rate(from, to)),
        "Frankfurter v2");
  }
}
