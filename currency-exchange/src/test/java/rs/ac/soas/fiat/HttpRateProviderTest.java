package rs.ac.soas.fiat;

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
        "/v2/",
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
            provider("{\"base\":\"EUR\",\"quote\":\"RSD\",\"rate\":117.5}", 200).rate("EUR", "RSD"))
        .isEqualByComparingTo("117.5");
  }

  @Test
  void failureAndMalformedDataAreHandled() throws Exception {
    assertThatThrownBy(() -> provider("{}", 503).rate("EUR", "RSD"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void missingRateIsHandled() throws Exception {
    assertThatThrownBy(() -> provider("{}", 200).rate("EUR", "RSD"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void catalogIncludesFiatOutsideOriginalListAndExcludesCryptoAndMetals() throws Exception {
    assertThat(
            provider(
                    """
                    [{"iso_code":"NZD","iso_numeric":"554"},
                     {"iso_code":"EUR","iso_numeric":"978"},
                     {"iso_code":"ETH","iso_numeric":null},
                     {"iso_code":"XAU","iso_numeric":"959"}]
                    """,
                    200)
                .currencies())
        .containsExactly("EUR", "NZD");
  }

  @Test
  void malformedCatalogIsHandled() throws Exception {
    assertThatThrownBy(() -> provider("{}", 200).currencies())
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("catalog");
  }
}
