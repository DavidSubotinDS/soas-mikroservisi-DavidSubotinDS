package rs.ac.soas.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "bank-account")
public interface BankClient {
  @GetMapping("/internal/bank")
  Api.Account account(@RequestParam("email") String email);

  @PostMapping("/internal/bank/provision")
  Api.Account provision(@RequestParam("email") String email);

  @DeleteMapping("/internal/bank")
  void delete(@RequestParam("email") String email);

  @PostMapping("/internal/bank/restore")
  void restore(@RequestBody Api.Account account);

  @PostMapping("/internal/bank/rename")
  void rename(@RequestBody Api.Rename rename);

  @PostMapping("/internal/bank/adjust")
  Api.Account adjust(@RequestBody Api.Adjustment adjustment);

  @PostMapping("/internal/bank/swap")
  Api.Account swap(@RequestBody Api.Swap swap);

  @PostMapping("/internal/bank/undo/{operationId}")
  void undo(@PathVariable("operationId") String operationId);
}
