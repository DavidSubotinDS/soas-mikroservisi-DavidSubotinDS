package rs.ac.soas.crypto;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import rs.ac.soas.contract.*;

@RestController
public class ExchangeController {
  private final ExchangeService exchange;

  public ExchangeController(ExchangeService exchange) {
    this.exchange = exchange;
  }

  @GetMapping("/crypto-exchange")
  public Api.Rate rate(@RequestParam String from, @RequestParam String to) {
    return exchange.rate(from, to);
  }

  @GetMapping("/crypto-exchange/assets")
  public List<String> assets() {
    return Assets.CRYPTO.stream().sorted().toList();
  }
}
