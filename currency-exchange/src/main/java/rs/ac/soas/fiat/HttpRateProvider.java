package rs.ac.soas.fiat;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import rs.ac.soas.util.*;

@Component
public class HttpRateProvider implements RateProvider {
  private final RestClient http;

  public HttpRateProvider(@Value("${soas.provider-url}") String baseUrl) {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(5));
    http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public List<String> currencies() {
    try {
      List<Map<String, Object>> response =
          http.get()
              .uri("/v2/currencies")
              .retrieve()
              .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
      if (response == null)
        throw BusinessException.unavailable("Fiat provider returned no currencies");
      // The provider also lists crypto and precious metals. ISO numeric codes identify
      // monetary currencies; the four ISO precious-metal codes are not fiat money.
      var metals = Set.of("XAU", "XAG", "XPD", "XPT");
      List<String> result =
          response.stream()
              .filter(row -> row.get("iso_numeric") != null)
              .map(row -> rs.ac.soas.contract.Assets.code(String.valueOf(row.get("iso_code"))))
              .filter(
                  code ->
                      code.matches("[A-Z]{3}")
                          && !metals.contains(code)
                          && !rs.ac.soas.contract.Assets.CRYPTO.contains(code))
              .distinct()
              .sorted()
              .toList();
      if (result.isEmpty())
        throw BusinessException.unavailable("Fiat provider returned no currencies");
      return result;
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw BusinessException.unavailable("Fiat currency catalog is unavailable or malformed");
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public BigDecimal rate(String from, String to) {
    try {
      Map<String, Object> response =
          http.get()
              .uri("/v2/rate/{from}/{to}", from, to)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      Object value = response == null ? null : response.get("rate");
      if (value == null)
        throw BusinessException.unavailable("fiat provider returned no rate for this pair");
      return Checks.rate(new BigDecimal(value.toString()));
    } catch (BusinessException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 422)
        throw BusinessException.bad("Unsupported fiat currency pair at provider");
      throw BusinessException.unavailable("fiat exchange provider is unavailable");
    } catch (Exception ex) {
      throw BusinessException.unavailable(
          "fiat exchange provider is unavailable or returned malformed data");
    }
  }
}
