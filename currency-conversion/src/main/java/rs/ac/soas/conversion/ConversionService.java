package rs.ac.soas.conversion;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@Service
public class ConversionService {
  private final BankClient bank;
  private final FiatClient rates;

  public ConversionService(BankClient bank, FiatClient rates) {
    this.bank = bank;
    this.rates = rates;
  }

  public Api.Transaction convert(Api.ConversionRequest input) {
    String email = Assets.email(input.email()),
        from = Checks.fiat(input.from()),
        to = Checks.fiat(input.to());
    BigDecimal quantity = Checks.positive(input.quantity());
    Api.Account current = bank.account(email);
    funds(current, from, quantity);
    BigDecimal rate =
        Checks.rate(input.quotedRate() == null ? rates.rate(from, to).rate() : input.quotedRate());
    BigDecimal received = Checks.positive(Assets.converted(quantity, rate));
    String operation =
        input.operationId() == null ? UUID.randomUUID().toString() : input.operationId();
    Api.Account result;
    try {
      result = bank.swap(new Api.Swap(email, from, to, quantity, received, operation));
    } catch (RuntimeException failure) {
      try {
        bank.undo(operation);
      } catch (RuntimeException compensation) {
        throw BusinessException.unavailable(
            "Conversion failed and compensation needs administrator recovery; operation "
                + operation);
      }
      throw failure;
    }
    return new Api.Transaction(
        from,
        to,
        quantity,
        received,
        result,
        null,
        "Exchanged "
            + quantity.stripTrailingZeros().toPlainString()
            + " "
            + from
            + " for "
            + received.stripTrailingZeros().toPlainString()
            + " "
            + to,
        operation);
  }

  private void funds(Api.Account account, String currency, BigDecimal quantity) {
    Api.Holding holding =
        account.holdings().stream()
            .filter(h -> h.currency().equals(currency))
            .findFirst()
            .orElseThrow(() -> BusinessException.missing("Missing bank holding: " + currency));
    if (holding.amount().compareTo(quantity) < 0)
      throw BusinessException.bad("Insufficient bank balance in " + currency);
  }
}
