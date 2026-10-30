package rs.ac.soas.util;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalErrors {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiError> business(BusinessException ex, HttpServletRequest req) {
    return error(ex.status(), ex.getMessage(), req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    return error(
        HttpStatus.BAD_REQUEST,
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .distinct()
            .sorted()
            .reduce((a, b) -> a + "; " + b)
            .orElse("Invalid request"),
        req);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    jakarta.validation.ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ApiError> malformed(Exception ex, HttpServletRequest req) {
    return error(
        HttpStatus.BAD_REQUEST,
        "Malformed request: check fields, role, currency codes and numeric amounts",
        req);
  }

  @ExceptionHandler(feign.FeignException.class)
  public ResponseEntity<ApiError> downstream(feign.FeignException ex, HttpServletRequest req) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "A required service could not complete the operation; please try again",
        req);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> duplicate(Exception ex, HttpServletRequest req) {
    return error(
        HttpStatus.CONFLICT,
        "Duplicate email, holding or OWNER; refresh the data and try again",
        req);
  }

  @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> concurrent(Exception ex, HttpServletRequest req) {
    return error(
        HttpStatus.CONFLICT, "Balance changed during this operation; refresh and try again", req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Request failed at {}", req.getRequestURI(), ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "The operation could not be completed", req);
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status, String message, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI()));
  }
}
