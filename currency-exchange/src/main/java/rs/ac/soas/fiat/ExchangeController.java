package rs.ac.soas.fiat;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.*;

@RestController
public class ExchangeController {
  private final ExchangeService exchange;

  public ExchangeController(ExchangeService exchange) {
    this.exchange = exchange;
  }

  @GetMapping("/currency-exchange")
  public Api.Rate rate(@RequestParam String from, @RequestParam String to) {
    return exchange.rate(from, to);
  }

  @GetMapping("/currency-exchange/assets")
  public List<String> assets() {
    return exchange.currencies();
  }
}
