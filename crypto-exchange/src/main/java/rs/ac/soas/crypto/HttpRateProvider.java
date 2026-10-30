package rs.ac.soas.crypto;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
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
  @SuppressWarnings("unchecked")
  public BigDecimal rate(String from, String to) {
    try {
      Map<String, Object> response =
          http.get()
              .uri("/v2/exchange-rates?currency={from}", from)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      Map<String, Object> data =
          response == null ? null : (Map<String, Object>) response.get("data");
      Map<String, Object> rates = data == null ? null : (Map<String, Object>) data.get("rates");
      Object value = rates == null ? null : rates.get(to);
      if (value == null)
        throw BusinessException.unavailable("crypto provider returned no rate for this pair");
      return Checks.rate(new BigDecimal(value.toString()));
    } catch (BusinessException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 422)
        throw BusinessException.bad("Unsupported crypto currency pair at provider");
      throw BusinessException.unavailable("crypto exchange provider is unavailable");
    } catch (Exception ex) {
      throw BusinessException.unavailable(
          "crypto exchange provider is unavailable or returned malformed data");
    }
  }
}
