package rs.ac.soas.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

class AuthenticationContractTest {
  @Test
  void callsUsersInternalContractAndReturnsRole() {
    WebClient.Builder client =
        WebClient.builder()
            .exchangeFunction(
                request -> {
                  assertThat(request.url().toString())
                      .isEqualTo("http://users-service/internal/auth/validate");
                  assertThat(request.headers().getFirst("X-Internal-Key")).isEqualTo("test-key");
                  return Mono.just(
                      ClientResponse.create(HttpStatus.OK)
                          .header("Content-Type", "application/json")
                          .body("{\"id\":1,\"email\":\"user@test.local\",\"role\":\"USER\"}")
                          .build());
                });
    var manager = new GatewaySecurity().authenticationManager(client, "test-key");
    var authenticated =
        manager
            .authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("user@test.local", "password"))
            .block();
    assertThat(authenticated).isNotNull();
    assertThat(authenticated.getCredentials()).isNull();
    assertThat(authenticated.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  void invalidCredentialsRemainUnauthorized() {
    var client =
        WebClient.builder()
            .exchangeFunction(
                request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build()));
    var manager = new GatewaySecurity().authenticationManager(client, "test-key");
    assertThatThrownBy(
            () ->
                manager
                    .authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                            "user@test.local", "wrong"))
                    .block())
        .isInstanceOf(BadCredentialsException.class);
  }
}
