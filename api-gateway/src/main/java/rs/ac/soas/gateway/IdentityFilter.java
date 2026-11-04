package rs.ac.soas.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import rs.ac.soas.contract.Api;

@Component
public class IdentityFilter implements GlobalFilter, Ordered {
  private final String key;

  public IdentityFilter(@Value("${soas.gateway-key}") String key) {
    this.key = key;
  }

  @Override
  public int getOrder() {
    return -10;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange
        .getPrincipal()
        .map(
            principal -> {
              Api.Identity identity =
                  ((Authentication) principal).getPrincipal() instanceof Api.Identity user
                      ? user
                      : null;
              return forward(exchange, identity);
            })
        .defaultIfEmpty(forward(exchange, null))
        .flatMap(chain::filter);
  }

  private ServerWebExchange forward(ServerWebExchange exchange, Api.Identity identity) {
    exchange.getResponse().getHeaders().setCacheControl("no-store");
    var request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.remove("Authorization");
                  headers.remove("X-Internal-Key");
                  headers.remove("X-Gateway-Key");
                  headers.remove("X-User-Email");
                  headers.remove("X-User-Role");
                  headers.set("X-Gateway-Key", key);
                  if (identity != null) {
                    headers.set("X-User-Email", identity.email());
                    headers.set("X-User-Role", identity.role().name());
                  }
                })
            .build();
    return exchange.mutate().request(request).build();
  }
}
