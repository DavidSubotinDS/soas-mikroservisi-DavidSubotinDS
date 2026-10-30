package rs.ac.soas.trade;

import feign.FeignException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import rs.ac.soas.util.*;

@Component
public class RateRetry {
  public <T> T execute(Supplier<T> read) {
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        return read.get();
      } catch (RuntimeException failure) {
        boolean retry =
            failure instanceof FeignException f && (f.status() < 0 || f.status() >= 500)
                || failure instanceof BusinessException b && b.status().value() == 503;
        if (!retry) throw failure;
        if (attempt == 3)
          throw BusinessException.unavailable(
              "Rate lookup failed after 3 attempts; no trade balances were changed");
        try {
          Thread.sleep(100L * attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw BusinessException.unavailable("Rate lookup interrupted");
        }
      }
    }
    throw new IllegalStateException("Unreachable");
  }
}
