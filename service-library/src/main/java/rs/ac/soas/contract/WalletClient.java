package rs.ac.soas.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "crypto-wallet")
public interface WalletClient {
  @GetMapping("/internal/wallet")
  Api.Account account(@RequestParam("email") String email);

  @PostMapping("/internal/wallet/provision")
  Api.Account provision(@RequestParam("email") String email);

  @DeleteMapping("/internal/wallet")
  void delete(@RequestParam("email") String email);

  @PostMapping("/internal/wallet/restore")
  void restore(@RequestBody Api.Account account);

  @PostMapping("/internal/wallet/rename")
  void rename(@RequestBody Api.Rename rename);

  @PostMapping("/internal/wallet/adjust")
  Api.Account adjust(@RequestBody Api.Adjustment adjustment);

  @PostMapping("/internal/wallet/swap")
  Api.Account swap(@RequestBody Api.Swap swap);

  @PostMapping("/internal/wallet/undo/{operationId}")
  void undo(@PathVariable("operationId") String operationId);
}
