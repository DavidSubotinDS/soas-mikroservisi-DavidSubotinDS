package rs.ac.soas.users;

import java.math.BigDecimal;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import rs.ac.soas.contract.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "soas.demo-data", havingValue = "true", matchIfMissing = true)
public class DemoData {
  private final UserService users;
  private final BankClient bank;
  private final WalletClient wallet;
  private boolean seeded;

  public DemoData(UserService users, BankClient bank, WalletClient wallet) {
    this.users = users;
    this.bank = bank;
    this.wallet = wallet;
  }

  @Bean
  ApplicationRunner managers() {
    return args -> users.seedManagers();
  }

  @Scheduled(initialDelay = 15000, fixedDelay = 10000)
  public void demoUser() {
    if (seeded) return;
    try {
      // Probe both services before creating anything: Eureka may still be warming its cache.
      bank.account("user@soas.local");
      wallet.account("user@soas.local");
      users.ensureDemoUserReady();
      bank.adjust(
          new Api.Adjustment(
              "user@soas.local", "EUR", new BigDecimal("10000"), "demo-bank-funding"));
      wallet.adjust(
          new Api.Adjustment("user@soas.local", "ETH", new BigDecimal("2"), "demo-wallet-funding"));
      seeded = true;
    } catch (RuntimeException ex) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .info("Waiting for account services before demo provisioning");
    }
  }
}
