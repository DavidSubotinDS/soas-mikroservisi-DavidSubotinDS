package rs.ac.soas.gateway;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.Api;

@RestController
public class AuthController {
  @GetMapping("/auth/me")
  public Api.Identity me(Authentication authentication) {
    return (Api.Identity) authentication.getPrincipal();
  }
}
