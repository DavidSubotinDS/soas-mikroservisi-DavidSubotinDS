package rs.ac.soas.gateway;

import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"eureka.client.enabled=false"})
class GatewayTest {
  static final HttpServer downstream;

  static {
    try {
      downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      downstream.createContext(
          "/",
          exchange -> {
            var h = exchange.getRequestHeaders();
            String email = h.getFirst("X-User-Email"), role = h.getFirst("X-User-Role");
            String json =
                "{\"email\":\""
                    + (email == null ? "" : email)
                    + "\",\"role\":\""
                    + (role == null ? "" : role)
                    + "\",\"path\":\""
                    + exchange.getRequestURI()
                    + "\",\"authorizationRemoved\":"
                    + (h.getFirst("Authorization") == null)
                    + ",\"internalKeyRemoved\":"
                    + (h.getFirst("X-Internal-Key") == null)
                    + ",\"gatewayKey\":\""
                    + h.getFirst("X-Gateway-Key")
                    + "\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      downstream.start();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry properties) {
    for (String name :
        List.of(
            "users-service",
            "bank-account",
            "crypto-wallet",
            "currency-exchange",
            "currency-conversion",
            "crypto-exchange",
            "trade-service"))
      properties.add(
          "spring.cloud.discovery.client.simple.instances." + name + "[0].uri",
          () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
  }

  @LocalServerPort int port;
  @MockitoBean ReactiveAuthenticationManager manager;
  WebTestClient client;

  @BeforeEach
  void setup() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    when(manager.authenticate(any()))
        .thenAnswer(
            i -> {
              var input = (org.springframework.security.core.Authentication) i.getArgument(0);
              if (!input.getCredentials().equals("valid"))
                return Mono.error(new BadCredentialsException("Invalid credentials"));
              String role = input.getName().split("@")[0].toUpperCase();
              var identity = new GatewayIdentity(1, input.getName(), role);
              return Mono.just(
                  new UsernamePasswordAuthenticationToken(
                      identity, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            });
  }

  @AfterAll
  static void stop() {
    downstream.stop(0);
  }

  @Test
  void publicRateRoutes() {
    for (String path :
        List.of("/currency-exchange?from=EUR&to=RSD", "/crypto-exchange?from=BTC&to=ETH"))
      client.get().uri(path).exchange().expectStatus().isOk();
  }

  @Test
  void noCredentialsAndBadCredentialsAreUnauthorized() {
    client
        .get()
        .uri("/users")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.message")
        .exists();
    client
        .get()
        .uri("/auth/me")
        .headers(h -> h.setBasicAuth("user@test.local", "wrong"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void roleMatrixIsEnforced() {
    for (String role : List.of("owner", "admin"))
      for (String path :
          List.of(
              "/currency-conversion?from=EUR&to=RSD&quantity=100",
              "/trade-service?from=EUR&to=BTC&quantity=100"))
        client
            .get()
            .uri(path)
            .headers(h -> h.setBasicAuth(role + "@test.local", "valid"))
            .exchange()
            .expectStatus()
            .isForbidden();
    client
        .get()
        .uri("/users")
        .headers(h -> h.setBasicAuth("user@test.local", "valid"))
        .exchange()
        .expectStatus()
        .isForbidden();
    for (String path : List.of("/bank-accounts", "/crypto-wallets"))
      client
          .get()
          .uri(path)
          .headers(h -> h.setBasicAuth("owner@test.local", "valid"))
          .exchange()
          .expectStatus()
          .isForbidden();
  }

  @Test
  void mandatoryRoutesPropagateTrustedIdentityAndRemoveSpoofedHeaders() {
    for (String path :
        List.of(
            "/currency-conversion?from=EUR&to=RSD&quantity=100",
            "/trade-service?from=EUR&to=BTC&quantity=100"))
      client
          .get()
          .uri(path)
          .headers(
              h -> {
                h.setBasicAuth("user@test.local", "valid");
                h.set("X-User-Email", "attacker@test.local");
                h.set("X-User-Role", "OWNER");
                h.set("X-Internal-Key", "spoofed");
                h.set("X-Gateway-Key", "spoofed");
              })
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.email")
          .isEqualTo("user@test.local")
          .jsonPath("$.role")
          .isEqualTo("USER")
          .jsonPath("$.path")
          .isEqualTo(path)
          .jsonPath("$.authorizationRemoved")
          .isEqualTo(true)
          .jsonPath("$.internalKeyRemoved")
          .isEqualTo(true)
          .jsonPath("$.gatewayKey")
          .isEqualTo("soas-local-gateway-change-me");
  }

  @Test
  void authMeReturnsIdentityWithoutPassword() {
    client
        .get()
        .uri("/auth/me")
        .headers(h -> h.setBasicAuth("admin@test.local", "valid"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.role")
        .isEqualTo("ADMIN")
        .jsonPath("$.password")
        .doesNotExist();
  }

  @Test
  void internalEndpointsCannotBeRoutedThroughGateway() {
    client
        .post()
        .uri("/internal/bank/adjust")
        .headers(h -> h.setBasicAuth("user@test.local", "valid"))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void corsAllowsFrontendPreflight() {
    client
        .options()
        .uri("/users")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,content-type")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
  }
}
