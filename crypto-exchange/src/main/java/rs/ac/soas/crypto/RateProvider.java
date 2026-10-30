package rs.ac.soas.crypto;

public interface RateProvider {
  java.math.BigDecimal rate(String from, String to);
}
