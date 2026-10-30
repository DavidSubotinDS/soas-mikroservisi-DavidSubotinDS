package rs.ac.soas.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "crypto-exchange")
public interface CryptoClient {
  @GetMapping("/crypto-exchange")
  Api.Rate rate(@RequestParam("from") String from, @RequestParam("to") String to);
}
