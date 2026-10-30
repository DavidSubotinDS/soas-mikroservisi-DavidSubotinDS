package rs.ac.soas.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "currency-conversion")
public interface ConversionClient {
  @PostMapping("/internal/conversion")
  Api.Transaction convert(@RequestBody Api.ConversionRequest request);
}
