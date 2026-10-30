package rs.ac.soas.bank;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@SpringBootTest(
    properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class AccountServiceTest {
  @Test
  void concurrentDebitsCannotOverdraw() throws Exception {
    accounts.provision(EMAIL);
    accounts.set(input("EUR", "100"), false);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      var calls = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 2; i++) {
        String operation = "concurrent-" + i;
        calls.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    accounts.adjust(new Api.Adjustment(EMAIL, "EUR", n("-80"), operation));
                    return true;
                  } catch (RuntimeException rejected) {
                    return false;
                  }
                }));
      }
      start.countDown();
      int successes = 0;
      for (var call : calls) if (call.get(10, java.util.concurrent.TimeUnit.SECONDS)) successes++;
      assertThat(successes).isEqualTo(1);
      assertThat(accounts.account(EMAIL).holdings().get(0).amount()).isEqualByComparingTo("20");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void creditOverflowIsRejectedWithoutChangingBalance() {
    accounts.provision(EMAIL);
    accounts.set(input("EUR", "99999999999999999999999999"), false);
    assertThatThrownBy(() -> accounts.adjust(new Api.Adjustment(EMAIL, "EUR", n("1"), "overflow")))
        .isInstanceOf(BusinessException.class);
    assertThat(accounts.account(EMAIL).holdings().get(0).amount())
        .isEqualByComparingTo("99999999999999999999999999");
  }

  @Autowired AccountService accounts;
  @Autowired AccountController controller;
  @Autowired HoldingRepository holdings;
  @Autowired LedgerRepository ledger;
  @MockitoBean UsersClient users;
  static final String EMAIL = "user@test.local";

  @BeforeEach
  void setup() {
    holdings.deleteAll();
    ledger.deleteAll();
    when(users.lookup(anyString())).thenReturn(new Api.Identity(1, EMAIL, Role.USER));
  }

  BigDecimal n(String value) {
    return new BigDecimal(value);
  }

  Api.HoldingInput input(String currency, String amount) {
    return new Api.HoldingInput(EMAIL, currency, n(amount));
  }

  MockHttpServletRequest request(Role role) {
    var req = new MockHttpServletRequest();
    req.addHeader("X-User-Role", role.name());
    req.addHeader("X-User-Email", EMAIL);
    return req;
  }

  @Test
  void defaultsAndUniqueHolding() {
    var a = accounts.provision(EMAIL);
    assertThat(a.holdings()).hasSize(1);
    assertThat(a.holdings().get(0).currency()).isEqualTo("EUR");
    assertThat(a.holdings().get(0).amount()).isEqualByComparingTo("0");
    accounts.provision(EMAIL);
    assertThat(holdings.count()).isEqualTo(1);
    assertThatThrownBy(() -> accounts.set(input("EUR", "2"), true))
        .hasMessageContaining("already exists");
  }

  @Test
  void onlyUserMayOwnHoldings() {
    when(users.lookup(anyString())).thenReturn(new Api.Identity(1, EMAIL, Role.ADMIN));
    assertThatThrownBy(() -> accounts.provision(EMAIL)).hasMessageContaining("Only USER");
  }

  @Test
  void negativeAndInsufficientAmountsAreRejected() {
    accounts.provision(EMAIL);
    assertThatThrownBy(() -> accounts.set(input("EUR", "-1"), false))
        .hasMessageContaining("negative");
    assertThatThrownBy(() -> accounts.adjust(new Api.Adjustment(EMAIL, "EUR", n("-1"), "debit")))
        .hasMessageContaining("Insufficient");
    assertThat(accounts.account(EMAIL).holdings().get(0).amount()).isEqualByComparingTo("0");
  }

  @Test
  void setBalanceCreditDebitAndIdempotency() {
    accounts.provision(EMAIL);
    accounts.set(input("EUR", "100"), false);
    var add = new Api.Adjustment(EMAIL, "EUR", n("20"), "credit");
    accounts.adjust(add);
    accounts.adjust(add);
    accounts.adjust(new Api.Adjustment(EMAIL, "EUR", n("-30"), "debit"));
    assertThat(accounts.account(EMAIL).holdings().get(0).amount()).isEqualByComparingTo("90");
    accounts.undo("debit");
    accounts.undo("debit");
    assertThat(accounts.account(EMAIL).holdings().get(0).amount()).isEqualByComparingTo("120");
  }

  @Test
  void atomicSwapCreatesTargetAndCanBeReversed() {
    accounts.provision(EMAIL);
    accounts.set(input("EUR", "100"), false);
    accounts.swap(new Api.Swap(EMAIL, "EUR", "USD", n("40"), n("2"), "swap"));
    assertThat(accounts.account(EMAIL).holdings()).hasSize(2);
    assertThat(holdings.findByEmailAndCurrency(EMAIL, "EUR").orElseThrow().amount)
        .isEqualByComparingTo("60");
    accounts.undo("swap");
    assertThat(holdings.findByEmailAndCurrency(EMAIL, "EUR").orElseThrow().amount)
        .isEqualByComparingTo("100");
  }

  @Test
  void failedSwapDoesNotCreateMoney() {
    accounts.provision(EMAIL);
    assertThatThrownBy(
            () -> accounts.swap(new Api.Swap(EMAIL, "EUR", "USD", n("1"), n("10"), "failed")))
        .isInstanceOf(BusinessException.class);
    assertThat(holdings.count()).isEqualTo(1);
  }

  @Test
  void adminCanManageAndUserCanOnlyViewOwn() {
    accounts.provision(EMAIL);
    controller.update(input("EUR", "30"), request(Role.ADMIN));
    controller.add(input("USD", "1"), request(Role.ADMIN));
    assertThat(controller.all(request(Role.ADMIN))).hasSize(1);
    assertThat(controller.own(request(Role.USER)).email()).isEqualTo(EMAIL);
    assertThatThrownBy(() -> controller.one("someone@test.local", request(Role.USER)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> controller.update(input("EUR", "100"), request(Role.USER)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void ownerForbidden() {
    assertThatThrownBy(() -> controller.all(request(Role.OWNER)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> controller.own(request(Role.OWNER)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> controller.add(input("EUR", "100"), request(Role.OWNER)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void lifecycleRenameAndDelete() {
    accounts.provision(EMAIL);
    accounts.rename(new Api.Rename(EMAIL, "new@test.local"));
    assertThat(accounts.account(EMAIL).holdings()).isEmpty();
    assertThat(accounts.account("new@test.local").holdings()).hasSize(1);
    accounts.delete("new@test.local");
    assertThat(holdings.count()).isZero();
  }
}
