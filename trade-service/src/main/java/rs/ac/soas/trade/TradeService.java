package rs.ac.soas.trade;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@Service
public class TradeService {
  private final BankClient bank;
  private final WalletClient wallet;
  private final CryptoClient crypto;
  private final FiatClient fiat;
  private final ConversionClient conversion;
  private final RateRetry retry;

  public TradeService(
      BankClient bank,
      WalletClient wallet,
      CryptoClient crypto,
      FiatClient fiat,
      ConversionClient conversion,
      RateRetry retry) {
    this.bank = bank;
    this.wallet = wallet;
    this.crypto = crypto;
    this.fiat = fiat;
    this.conversion = conversion;
    this.retry = retry;
  }

  public Api.Transaction trade(
      String rawEmail, String rawFrom, String rawTo, BigDecimal rawQuantity) {
    String email = Assets.email(rawEmail), from = Assets.code(rawFrom), to = Assets.code(rawTo);
    BigDecimal quantity = Checks.positive(rawQuantity);
    Set<String> assets = new HashSet<>(Assets.FIAT);
    assets.addAll(Assets.CRYPTO);
    Checks.asset(from, assets, "asset");
    Checks.asset(to, assets, "asset");
    boolean sourceCrypto = Assets.CRYPTO.contains(from), targetCrypto = Assets.CRYPTO.contains(to);
    if (!sourceCrypto && !targetCrypto)
      throw BusinessException.bad("Use currency-conversion for fiat to fiat");
    Api.Account source = sourceCrypto ? wallet.account(email) : bank.account(email);
    funds(source, from, quantity);
    String operation = UUID.randomUUID().toString();
    List<Runnable> undo = new ArrayList<>();
    BigDecimal received;
    Api.Account bankResult = null, walletResult = null;
    // All quotes are obtained before the first mutation. Only these read calls are retried.
    if (sourceCrypto && targetCrypto) {
      received = Checks.positive(Assets.converted(quantity, cryptoRate(from, to)));
      try {
        undo.add(() -> wallet.undo(operation + ":swap"));
        walletResult =
            wallet.swap(new Api.Swap(email, from, to, quantity, received, operation + ":swap"));
      } catch (RuntimeException failure) {
        rollback(operation, undo, failure);
        throw failure;
      }
    } else if (!sourceCrypto) {
      boolean bridge = !Set.of("EUR", "USD").contains(from);
      String settlement = bridge ? "USD" : from;
      BigDecimal fiatRate = bridge ? fiatRate(from, settlement) : BigDecimal.ONE;
      BigDecimal intermediate = Checks.positive(Assets.converted(quantity, fiatRate));
      received = Checks.positive(Assets.converted(intermediate, cryptoRate(settlement, to)));
      try {
        if (bridge) {
          undo.add(() -> bank.undo(operation + ":pre-conversion"));
          conversion.convert(
              new Api.ConversionRequest(
                  email, from, settlement, quantity, fiatRate, operation + ":pre-conversion"));
        }
        undo.add(() -> bank.undo(operation + ":bank"));
        bank.adjust(
            new Api.Adjustment(email, settlement, intermediate.negate(), operation + ":bank"));
        undo.add(() -> wallet.undo(operation + ":wallet"));
        walletResult =
            wallet.adjust(new Api.Adjustment(email, to, received, operation + ":wallet"));
      } catch (RuntimeException failure) {
        rollback(operation, undo, failure);
        throw failure;
      }
    } else {
      boolean bridge = !Set.of("EUR", "USD").contains(to);
      String settlement = bridge ? "USD" : to;
      BigDecimal intermediate =
          Checks.positive(Assets.converted(quantity, cryptoRate(from, settlement)));
      BigDecimal fiatRate = bridge ? fiatRate(settlement, to) : BigDecimal.ONE;
      received = Checks.positive(Assets.converted(intermediate, fiatRate));
      try {
        undo.add(() -> wallet.undo(operation + ":wallet"));
        wallet.adjust(new Api.Adjustment(email, from, quantity.negate(), operation + ":wallet"));
        undo.add(() -> bank.undo(operation + ":bank"));
        bankResult =
            bank.adjust(new Api.Adjustment(email, settlement, intermediate, operation + ":bank"));
        if (bridge) {
          undo.add(() -> bank.undo(operation + ":post-conversion"));
          bankResult =
              conversion
                  .convert(
                      new Api.ConversionRequest(
                          email,
                          settlement,
                          to,
                          intermediate,
                          fiatRate,
                          operation + ":post-conversion"))
                  .bankAccount();
        }
      } catch (RuntimeException failure) {
        rollback(operation, undo, failure);
        throw failure;
      }
    }
    return new Api.Transaction(
        from,
        to,
        quantity,
        received,
        bankResult,
        walletResult,
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

  private BigDecimal cryptoRate(String from, String to) {
    return Checks.rate(retry.execute(() -> crypto.rate(from, to)).rate());
  }

  private BigDecimal fiatRate(String from, String to) {
    return Checks.rate(retry.execute(() -> fiat.rate(from, to)).rate());
  }

  private void funds(Api.Account account, String currency, BigDecimal quantity) {
    Api.Holding holding =
        account.holdings().stream()
            .filter(h -> h.currency().equals(currency))
            .findFirst()
            .orElseThrow(() -> BusinessException.missing("Missing source holding: " + currency));
    if (holding.amount().compareTo(quantity) < 0)
      throw BusinessException.bad("Insufficient balance in " + currency);
  }

  private void rollback(String operation, List<Runnable> undo, RuntimeException failure) {
    boolean failed = false;
    for (int i = undo.size() - 1; i >= 0; i--)
      try {
        undo.get(i).run();
      } catch (RuntimeException ex) {
        failure.addSuppressed(ex);
        failed = true;
      }
    if (failed) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .error("Compensation incomplete for {}", operation, failure);
      throw BusinessException.unavailable(
          "Trade failed; compensation requires administrator recovery. Operation " + operation);
    }
  }
}
