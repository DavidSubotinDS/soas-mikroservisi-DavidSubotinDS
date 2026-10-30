package rs.ac.soas.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "users-service")
public interface UsersClient {
  @GetMapping("/internal/users/lookup")
  Api.Identity lookup(@RequestParam("email") String email);
}
