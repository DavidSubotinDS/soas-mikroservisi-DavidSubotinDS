package rs.ac.soas.contract;

import java.math.*;
import java.util.*;

public final class Assets {
  public static final Set<String> CRYPTO =
      Set.of("BTC", "ETH", "SOL", "ADA", "XRP", "LTC", "DOGE", "BCH");

  private Assets() {}

  public static String code(String raw) {
    return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
  }

  public static String email(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }

  public static BigDecimal amount(BigDecimal value) {
    return value.setScale(12, RoundingMode.HALF_EVEN);
  }

  public static BigDecimal converted(BigDecimal quantity, BigDecimal rate) {
    return amount(quantity.multiply(rate));
  }
}
