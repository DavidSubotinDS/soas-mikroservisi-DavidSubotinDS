package rs.ac.soas.gateway;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayFailureHandler implements ErrorWebExceptionHandler {
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
    if (exchange.getResponse().isCommitted()) return Mono.error(failure);
    return GatewayErrors.write(exchange, 503, "A required service is currently unavailable");
  }
}
