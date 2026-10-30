package rs.ac.soas.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class ServiceSecurityTest {
  static class Registry extends InterceptorRegistry {
    HandlerInterceptor first() {
      return (HandlerInterceptor) getInterceptors().get(0);
    }
  }

  HandlerInterceptor guard() {
    var registry = new Registry();
    new ServiceConfiguration("internal-key", "gateway-key").addInterceptors(registry);
    return registry.first();
  }

  @Test
  void internalMutationsRequireServiceAuthentication() throws Exception {
    var request = new MockHttpServletRequest("POST", "/internal/bank/adjust");
    assertThatThrownBy(
            () -> guard().preHandle(request, new MockHttpServletResponse(), new Object()))
        .isInstanceOfSatisfying(
            BusinessException.class, error -> assertThat(error.status().value()).isEqualTo(401));
    request.addHeader("X-Internal-Key", "internal-key");
    assertThat(guard().preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
  }

  @Test
  void identityHeadersAloneCannotBypassGateway() throws Exception {
    var request = new MockHttpServletRequest("GET", "/bank-accounts");
    request.addHeader("X-User-Role", "ADMIN");
    request.addHeader("X-User-Email", "admin@soas.local");
    assertThatThrownBy(
            () -> guard().preHandle(request, new MockHttpServletResponse(), new Object()))
        .isInstanceOf(BusinessException.class);
    request.addHeader("X-Gateway-Key", "gateway-key");
    assertThat(guard().preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
  }

  @Test
  void onlyExplicitRatePathsArePublic() throws Exception {
    for (String path :
        java.util.List.of(
            "/currency-exchange",
            "/crypto-exchange",
            "/currency-exchange/assets",
            "/crypto-exchange/assets"))
      assertThat(
              guard()
                  .preHandle(
                      new MockHttpServletRequest("GET", path),
                      new MockHttpServletResponse(),
                      new Object()))
          .isTrue();
    assertThatThrownBy(
            () ->
                guard()
                    .preHandle(
                        new MockHttpServletRequest("GET", "/users/assets"),
                        new MockHttpServletResponse(),
                        new Object()))
        .isInstanceOf(BusinessException.class);
  }
}
