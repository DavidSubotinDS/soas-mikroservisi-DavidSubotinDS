package rs.ac.soas.gateway;

import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;

@Configuration
public class GatewayRoutes {
  @Bean
  RouteLocator routes(RouteLocatorBuilder builder) {
    return builder
        .routes()
        .route("users", r -> r.path("/users", "/users/**").uri("lb://users-service"))
        .route("bank", r -> r.path("/bank-accounts", "/bank-accounts/**").uri("lb://bank-account"))
        .route(
            "wallet",
            r -> r.path("/crypto-wallets", "/crypto-wallets/**").uri("lb://crypto-wallet"))
        .route(
            "fiat",
            r ->
                r.path("/currency-exchange", "/currency-exchange/assets")
                    .uri("lb://currency-exchange"))
        .route(
            "crypto",
            r -> r.path("/crypto-exchange", "/crypto-exchange/assets").uri("lb://crypto-exchange"))
        .route("conversion", r -> r.path("/currency-conversion").uri("lb://currency-conversion"))
        .route("trade", r -> r.path("/trade-service").uri("lb://trade-service"))
        .build();
  }
}
