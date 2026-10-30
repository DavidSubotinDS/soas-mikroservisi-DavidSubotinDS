package rs.ac.soas.conversion;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@RestController
public class ConversionController {
  private final ConversionService conversion;

  public ConversionController(ConversionService conversion) {
    this.conversion = conversion;
  }

  @GetMapping("/currency-conversion")
  public Api.Transaction convert(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam BigDecimal quantity,
      HttpServletRequest request) {
    Access.require(request, Role.USER);
    return conversion.convert(
        new Api.ConversionRequest(Access.email(request), from, to, quantity, null, null));
  }

  @PostMapping("/internal/conversion")
  public Api.Transaction internal(@RequestBody Api.ConversionRequest input) {
    return conversion.convert(input);
  }
}
