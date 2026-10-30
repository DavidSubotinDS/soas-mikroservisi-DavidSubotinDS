package rs.ac.soas.util;

import jakarta.servlet.http.HttpServletRequest;
import rs.ac.soas.contract.*;

public final class Access {
  private Access() {}

  public static Role role(HttpServletRequest request) {
    try {
      return Role.valueOf(request.getHeader("X-User-Role"));
    } catch (Exception e) {
      throw BusinessException.forbidden();
    }
  }

  public static String email(HttpServletRequest request) {
    String email = Assets.email(request.getHeader("X-User-Email"));
    if (email.isBlank()) throw BusinessException.forbidden();
    return email;
  }

  public static void require(HttpServletRequest request, Role... allowed) {
    Role role = role(request);
    if (java.util.Arrays.stream(allowed).noneMatch(r -> r == role))
      throw BusinessException.forbidden();
  }

  public static void account(HttpServletRequest request, String email) {
    require(request, Role.ADMIN, Role.USER);
    if (role(request) == Role.USER && !email(request).equals(Assets.email(email)))
      throw BusinessException.forbidden();
  }
}
