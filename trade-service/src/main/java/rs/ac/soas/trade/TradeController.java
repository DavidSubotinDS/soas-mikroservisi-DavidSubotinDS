package rs.ac.soas.trade;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@RestController
public class TradeController {
  private final TradeService trade;

  public TradeController(TradeService trade) {
    this.trade = trade;
  }

  @GetMapping("/trade-service")
  public Api.Transaction trade(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam BigDecimal quantity,
      HttpServletRequest request) {
    Access.require(request, Role.USER);
    return trade.trade(Access.email(request), from, to, quantity);
  }
}
