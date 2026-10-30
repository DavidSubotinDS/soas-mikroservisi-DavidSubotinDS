package rs.ac.soas.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "currency-exchange")
public interface FiatClient {
  @GetMapping("/currency-exchange")
  Api.Rate rate(@RequestParam("from") String from, @RequestParam("to") String to);
}
