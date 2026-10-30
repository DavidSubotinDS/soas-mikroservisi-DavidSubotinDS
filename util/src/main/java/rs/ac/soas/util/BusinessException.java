package rs.ac.soas.util;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  private final HttpStatus status;

  public BusinessException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static BusinessException bad(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, message);
  }

  public static BusinessException forbidden() {
    return new BusinessException(
        HttpStatus.FORBIDDEN, "This role is not permitted to perform this operation");
  }

  public static BusinessException missing(String message) {
    return new BusinessException(HttpStatus.NOT_FOUND, message);
  }

  public static BusinessException conflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, message);
  }

  public static BusinessException unavailable(String message) {
    return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
