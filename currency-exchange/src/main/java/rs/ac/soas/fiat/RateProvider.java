package rs.ac.soas.fiat;

public interface RateProvider {
  java.util.List<String> currencies();

  java.math.BigDecimal rate(String from, String to);
}
