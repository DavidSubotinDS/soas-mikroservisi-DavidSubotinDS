package rs.ac.soas.gateway;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.*;
import org.springframework.web.cors.reactive.*;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import rs.ac.soas.contract.Api;

@Configuration
public class GatewaySecurity {
  @Bean
  @LoadBalanced
  WebClient.Builder discoveryWebClient() {
    return WebClient.builder();
  }

  @Bean
  ReactiveAuthenticationManager authenticationManager(
      WebClient.Builder builder, @Value("${soas.internal-key}") String key) {
    WebClient client = builder.build();
    return authentication ->
        client
            .post()
            .uri("http://users-service/internal/auth/validate")
            .header("X-Internal-Key", key)
            .bodyValue(new Api.Credentials(authentication.getName(),
                authentication.getCredentials().toString()))
            .retrieve()
            .onStatus(
                status -> status.value() == 401,
                response -> Mono.error(new BadCredentialsException("Invalid email or password")))
            .bodyToMono(Api.Identity.class)
            .timeout(Duration.ofSeconds(8))
            .map(
                user ->
                    (org.springframework.security.core.Authentication)
                        new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))))
            .onErrorMap(
                error -> !(error instanceof AuthenticationException),
                error ->
                    new AuthenticationServiceException(
                        "Authentication service unavailable", error));
  }

  @Bean
  SecurityWebFilterChain security(
      ServerHttpSecurity http,
      ReactiveAuthenticationManager manager,
      CorsConfigurationSource cors) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(c -> c.configurationSource(cors))
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authenticationManager(manager)
        .authorizeExchange(
            a ->
                a.pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .pathMatchers(
                        "/currency-exchange",
                        "/currency-exchange/assets",
                        "/crypto-exchange",
                        "/crypto-exchange/assets",
                        "/actuator/health")
                    .permitAll()
                    .pathMatchers("/users", "/users/**")
                    .hasAnyRole("OWNER", "ADMIN")
                    .pathMatchers(
                        "/bank-accounts",
                        "/bank-accounts/**",
                        "/crypto-wallets",
                        "/crypto-wallets/**")
                    .hasAnyRole("ADMIN", "USER")
                    .pathMatchers("/currency-conversion", "/trade-service")
                    .hasRole("USER")
                    .pathMatchers("/auth/me")
                    .authenticated()
                    .anyExchange()
                    .denyAll())
        .httpBasic(
            b ->
                b.authenticationEntryPoint(
                    (exchange, error) ->
                        GatewayErrors.write(
                            exchange,
                            error instanceof AuthenticationServiceException ? 503 : 401,
                            error instanceof AuthenticationServiceException
                                ? "Authentication service unavailable"
                                : "Basic Authentication is required")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (exchange, error) ->
                            GatewayErrors.write(exchange, 401, "Basic Authentication is required"))
                    .accessDeniedHandler(
                        (exchange, error) ->
                            GatewayErrors.write(
                                exchange,
                                403,
                                "This role is not permitted to access this resource")))
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${soas.frontend-origin}") String origin) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(origin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
