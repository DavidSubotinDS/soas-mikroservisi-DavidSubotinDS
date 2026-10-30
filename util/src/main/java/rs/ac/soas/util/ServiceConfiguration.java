package rs.ac.soas.util;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@Import(GlobalErrors.class)
public class ServiceConfiguration implements WebMvcConfigurer {
  private final String internalKey, gatewayKey;

  public ServiceConfiguration(
      @Value("${soas.internal-key}") String internalKey,
      @Value("${soas.gateway-key}") String gatewayKey) {
    this.internalKey = internalKey;
    this.gatewayKey = gatewayKey;
  }

  @Bean
  public feign.RequestInterceptor internalAuthentication() {
    return template -> template.header("X-Internal-Key", internalKey);
  }

  @Bean
  public feign.Retryer noMutationRetry() {
    return feign.Retryer.NEVER_RETRY;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest req, HttpServletResponse res, Object handler) {
            String path = req.getRequestURI();
            if (path.startsWith("/actuator/") || path.equals("/error")) return true;
            if (path.startsWith("/internal/")) {
              if (!equal(internalKey, req.getHeader("X-Internal-Key")))
                throw new BusinessException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Internal service authentication required");
              return true;
            }
            if ((path.equals("/currency-exchange")
                    || path.equals("/crypto-exchange")
                    || path.equals("/currency-exchange/assets")
                    || path.equals("/crypto-exchange/assets"))
                && req.getMethod().equals("GET")) return true;
            if (!equal(gatewayKey, req.getHeader("X-Gateway-Key")))
              throw new BusinessException(
                  org.springframework.http.HttpStatus.UNAUTHORIZED,
                  "Use the API Gateway with Basic Authentication");
            return true;
          }
        });
  }

  private static boolean equal(String expected, String supplied) {
    return supplied != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }
}
