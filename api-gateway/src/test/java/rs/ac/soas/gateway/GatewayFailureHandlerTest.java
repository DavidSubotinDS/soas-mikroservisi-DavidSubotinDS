package rs.ac.soas.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.*;
import tools.jackson.databind.json.JsonMapper;

class GatewayFailureHandlerTest {
  @Test
  void frameworkStatusAndAllowHeaderArePreserved() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/me"));
    new GatewayFailureHandler()
        .handle(exchange, new MethodNotAllowedException("POST", java.util.List.of(HttpMethod.GET)))
        .block();
    assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(405);
    assertThat(exchange.getResponse().getHeaders().getAllow()).contains(HttpMethod.GET);
    assertThat(exchange.getResponse().getBodyAsString().block())
        .contains("Method Not Allowed")
        .doesNotContain("stackTrace", "trace");
  }

  @Test
  void networkFailureIs503AndUnknownBugIs500WithoutLeakingDetails() {
    var handler = new GatewayFailureHandler();
    var unavailable = MockServerWebExchange.from(MockServerHttpRequest.get("/trade-service"));
    handler.handle(unavailable, new java.net.ConnectException("private detail")).block();
    assertThat(unavailable.getResponse().getStatusCode().value()).isEqualTo(503);
    var bug = MockServerWebExchange.from(MockServerHttpRequest.get("/trade-service"));
    handler.handle(bug, new IllegalStateException("private detail")).block();
    assertThat(bug.getResponse().getStatusCode().value()).isEqualTo(500);
    assertThat(bug.getResponse().getBodyAsString().block()).doesNotContain("private detail");
  }

  @Test
  void errorContractUsesJsonEscaping() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth/me"));
    GatewayErrors.write(exchange, 400, "A \"quoted\" message\nwith a newline").block();
    var body =
        JsonMapper.builder().build().readTree(exchange.getResponse().getBodyAsString().block());
    assertThat(body.path("message").asString()).isEqualTo("A \"quoted\" message\nwith a newline");
    assertThat(body.path("timestamp").asString()).isNotBlank();
  }
}
