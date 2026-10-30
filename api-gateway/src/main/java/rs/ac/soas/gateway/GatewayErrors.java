package rs.ac.soas.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class GatewayErrors {
  private GatewayErrors() {}

  public static Mono<Void> write(ServerWebExchange exchange, int status, String message) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatusCode.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    response.getHeaders().setCacheControl("no-store");
    if (status == 401) response.getHeaders().set("WWW-Authenticate", "Basic realm=SOAS");
    String path = exchange.getRequest().getPath().value().replace("\\", "_").replace("\"", "_");
    String json =
        "{\"timestamp\":\""
            + Instant.now()
            + "\",\"status\":"
            + status
            + ",\"error\":\""
            + HttpStatus.valueOf(status).getReasonPhrase()
            + "\",\"message\":\""
            + message
            + "\",\"path\":\""
            + path
            + "\"}";
    return response.writeWith(
        Mono.just(response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8))));
  }
}
