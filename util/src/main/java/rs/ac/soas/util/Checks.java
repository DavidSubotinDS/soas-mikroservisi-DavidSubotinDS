package rs.ac.soas.util;

import java.math.BigDecimal;
import java.util.Set;
import rs.ac.soas.contract.*;

public final class Checks {
  private Checks() {}

  public static String fiat(String raw) {
    return asset(raw, Assets.FIAT, "fiat currency");
  }

  public static String crypto(String raw) {
    return asset(raw, Assets.CRYPTO, "crypto currency");
  }

  public static String asset(String raw, Set<String> allowed, String kind) {
    String code = Assets.code(raw);
    if (!allowed.contains(code)) throw BusinessException.bad("Unsupported " + kind + ": " + code);
    return code;
  }

  public static BigDecimal positive(BigDecimal value) {
    if (value == null || value.signum() <= 0)
      throw BusinessException.bad("Quantity must be greater than zero");
    BigDecimal amount = nonnegative(value);
    if (amount.signum() == 0)
      throw BusinessException.bad("Quantity is below the supported precision");
    return amount;
  }

  public static BigDecimal nonnegative(BigDecimal value) {
    if (value == null || value.signum() < 0)
      throw BusinessException.bad("Amount cannot be negative");
    if (value.precision() - value.scale() > 26 || value.scale() > 12)
      throw BusinessException.bad(
          "Amount supports at most 26 integer digits and 12 decimal places");
    return Assets.amount(value);
  }

  public static BigDecimal rate(BigDecimal rate) {
    if (rate == null || rate.signum() <= 0)
      throw BusinessException.unavailable("Exchange provider returned an invalid rate");
    return rate;
  }
}
