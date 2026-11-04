package rs.ac.soas.util;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import rs.ac.soas.contract.ApiError;
import tools.jackson.databind.json.JsonMapper;

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
    if (ex.status() >= 400 && ex.status() < 500) {
      HttpStatusCode status = HttpStatusCode.valueOf(ex.status());
      String message = reason(status);
      // Forward only our structured error contract, never raw upstream bodies or traces.
      if (ex.responseBody().isPresent() && ex.responseBody().get().remaining() <= 16_384) {
        try {
          var body = JsonMapper.builder().build().readTree(ex.contentUTF8());
          if (body.path("status").asInt() == ex.status()
              && body.path("error").isString()
              && body.path("message").isString()
              && !body.has("trace")
              && !body.has("stackTrace")) {
            String detail = body.path("message").asString();
            if (!detail.isBlank()
                && detail.length() <= 1000
                && detail.indexOf('\n') < 0
                && detail.indexOf('\r') < 0) message = detail;
          }
        } catch (Exception ignored) {
          // Malformed upstream bodies must not mask the HTTP status.
        }
      }
      return error(status, message, req);
    }
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
    if (ex instanceof ErrorResponse framework) {
      var response = error(framework.getStatusCode(), reason(framework.getStatusCode()), req);
      return ResponseEntity.status(response.getStatusCode())
          .headers(framework.getHeaders())
          .body(response.getBody());
    }
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error("Request failed at {}", req.getRequestURI(), ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "The operation could not be completed", req);
  }

  private ResponseEntity<ApiError> error(
      HttpStatusCode status, String message, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                Instant.now(), status.value(), reason(status), message, req.getRequestURI()));
  }

  private static String reason(HttpStatusCode code) {
    HttpStatus known = HttpStatus.resolve(code.value());
    return known == null ? "Request failed" : known.getReasonPhrase();
  }
}
