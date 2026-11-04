package rs.ac.soas.gateway;

import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import rs.ac.soas.contract.ApiError;
import tools.jackson.databind.json.JsonMapper;

public final class GatewayErrors {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private GatewayErrors() {}

  public static Mono<Void> write(ServerWebExchange exchange, int status, String message) {
    var response = exchange.getResponse();
    response.setStatusCode(HttpStatusCode.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    response.getHeaders().setCacheControl("no-store");
    if (status == 401) response.getHeaders().set("WWW-Authenticate", "Basic realm=SOAS");
    var known = HttpStatus.resolve(status);
    byte[] json =
        JSON.writeValueAsBytes(
            new ApiError(
                Instant.now(),
                status,
                known == null ? "Request failed" : known.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value()));
    return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
  }
}
