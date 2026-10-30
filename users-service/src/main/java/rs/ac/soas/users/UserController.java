package rs.ac.soas.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@RestController
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping("/users")
  public List<Api.Identity> all(HttpServletRequest req) {
    return users.all(Access.role(req));
  }

  @GetMapping("/users/{id}")
  public Api.Identity get(@PathVariable long id, HttpServletRequest req) {
    return users.get(id, Access.role(req));
  }

  @PostMapping("/users")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Api.Identity create(@Valid @RequestBody Api.UserInput input, HttpServletRequest req) {
    return users.create(input, Access.role(req));
  }

  @PutMapping("/users/{id}")
  public Api.Identity update(
      @PathVariable long id, @Valid @RequestBody Api.UserUpdate input, HttpServletRequest req) {
    return users.update(id, input, Access.role(req));
  }

  @DeleteMapping("/users/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable long id, HttpServletRequest req) {
    users.delete(id, Access.role(req));
  }

  @PostMapping("/internal/auth/validate")
  public Api.Identity authenticate(@Valid @RequestBody Api.Credentials input) {
    return users.authenticate(input);
  }

  @GetMapping("/internal/users/lookup")
  public Api.Identity lookup(@RequestParam String email) {
    return users.lookup(email);
  }
}
