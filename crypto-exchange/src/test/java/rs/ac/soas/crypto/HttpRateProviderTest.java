package rs.ac.soas.crypto;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import rs.ac.soas.util.BusinessException;

class HttpRateProviderTest {
  HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  HttpRateProvider provider(String body, int status) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v2/exchange-rates",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return new HttpRateProvider("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @Test
  void exactProviderContract() throws Exception {
    assertThat(
            provider("{\"data\":{\"currency\":\"BTC\",\"rates\":{\"ETH\":\"20.25\"}}}", 200)
                .rate("BTC", "ETH"))
        .isEqualByComparingTo("20.25");
  }

  @Test
  void failureAndMalformedDataAreHandled() throws Exception {
    assertThatThrownBy(() -> provider("{}", 503).rate("BTC", "ETH"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void missingRateIsHandled() throws Exception {
    assertThatThrownBy(() -> provider("{}", 200).rate("BTC", "ETH"))
        .isInstanceOf(BusinessException.class);
  }
}
