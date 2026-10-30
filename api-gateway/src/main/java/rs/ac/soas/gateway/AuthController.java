package rs.ac.soas.gateway;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  @GetMapping("/auth/me")
  public GatewayIdentity me(Authentication authentication) {
    return (GatewayIdentity) authentication.getPrincipal();
  }
}
