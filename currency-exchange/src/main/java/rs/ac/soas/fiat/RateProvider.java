package rs.ac.soas.fiat;

public interface RateProvider {
  java.math.BigDecimal rate(String from, String to);
}
