package rs.ac.soas.gateway;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayFailureHandler implements ErrorWebExceptionHandler {
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
    if (exchange.getResponse().isCommitted()) return Mono.error(failure);
    if (failure instanceof ErrorResponse framework) {
      var status = framework.getStatusCode();
      exchange.getResponse().getHeaders().addAll(framework.getHeaders());
      var known = HttpStatus.resolve(status.value());
      return GatewayErrors.write(
          exchange, status.value(), known == null ? "Request failed" : known.getReasonPhrase());
    }
    if (failure instanceof java.io.IOException
        || failure instanceof java.util.concurrent.TimeoutException
        || failure
            instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
      return GatewayErrors.write(exchange, 503, "A required service is currently unavailable");
    }
    org.slf4j.LoggerFactory.getLogger(getClass()).error("Gateway request failed", failure);
    return GatewayErrors.write(exchange, 500, "The operation could not be completed");
  }
}
