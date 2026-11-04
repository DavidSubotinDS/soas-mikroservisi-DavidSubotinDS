package rs.ac.soas.trade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

class TradeTest {
  BankClient bank = mock(BankClient.class);
  WalletClient wallet = mock(WalletClient.class);
  CryptoClient crypto = mock(CryptoClient.class);
  FiatClient fiat = mock(FiatClient.class);
  ConversionClient conversion = mock(ConversionClient.class);
  TradeService trade =
      new TradeService(
          bank, wallet, crypto, fiat, conversion, new RateRetry(), new FiatCurrencies(fiat));
  final String email = "user@test.local";
  Api.Account account =
      new Api.Account(
          email,
          List.of(
              new Api.Holding(1, "EUR", new BigDecimal("1000")),
              new Api.Holding(2, "USD", new BigDecimal("1000")),
              new Api.Holding(3, "RSD", new BigDecimal("1000")),
              new Api.Holding(4, "ETH", new BigDecimal("1000")),
              new Api.Holding(5, "BTC", new BigDecimal("1000")),
              new Api.Holding(6, "NZD", new BigDecimal("1000"))));

  @BeforeEach
  void setup() {
    when(fiat.currencies()).thenReturn(List.of("EUR", "RSD", "USD", "NZD"));
    when(bank.account(email)).thenReturn(account);
    when(wallet.account(email)).thenReturn(account);
    when(crypto.rate(anyString(), anyString()))
        .thenAnswer(
            i -> new Api.Rate(i.getArgument(0), i.getArgument(1), new BigDecimal("2"), "mock"));
    when(fiat.rate(anyString(), anyString()))
        .thenAnswer(
            i -> new Api.Rate(i.getArgument(0), i.getArgument(1), new BigDecimal("3"), "mock"));
    when(bank.adjust(any())).thenReturn(account);
    when(wallet.adjust(any())).thenReturn(account);
    when(wallet.swap(any())).thenReturn(account);
    when(conversion.convert(any()))
        .thenAnswer(
            i -> {
              Api.ConversionRequest r = i.getArgument(0);
              return new Api.Transaction(
                  r.from(),
                  r.to(),
                  r.quantity(),
                  r.quantity().multiply(r.quotedRate()),
                  account,
                  null,
                  "Converted",
                  r.operationId());
            });
  }

  @ParameterizedTest
  @CsvSource({
    "ETH,BTC,20",
    "EUR,BTC,20",
    "USD,ETH,20",
    "RSD,BTC,60",
    "ETH,EUR,20",
    "BTC,USD,20",
    "ETH,RSD,60",
    "NZD,BTC,60",
    "ETH,NZD,60"
  })
  void allRequiredModes(String from, String to, String expected) {
    Api.Transaction result = trade.trade(email, from, to, BigDecimal.TEN);
    assertThat(result.received()).isEqualByComparingTo(expected);
    assertThat(result.report()).contains(from, to);
    if (Assets.CRYPTO.contains(to)) assertThat(result.wallet()).isNotNull();
    else assertThat(result.bankAccount()).isNotNull();
    if (from.equals("RSD") || from.equals("NZD")) {
      verify(conversion)
          .convert(
              argThat(
                  r ->
                      r.from().equals(from)
                          && r.to().equals("USD")
                          && r.quantity().compareTo(BigDecimal.TEN) == 0));
      verify(bank)
          .adjust(
              argThat(
                  a ->
                      a.currency().equals("USD")
                          && a.delta().compareTo(new BigDecimal("-30")) == 0));
    }
    if (to.equals("RSD") || to.equals("NZD"))
      verify(conversion)
          .convert(
              argThat(
                  r ->
                      r.from().equals("USD")
                          && r.to().equals(to)
                          && r.quantity().compareTo(new BigDecimal("20")) == 0));
  }

  @ParameterizedTest
  @CsvSource({"EUR,BTC", "ETH,USD"})
  void insufficientFunds(String from, String to) {
    assertThatThrownBy(() -> trade.trade(email, from, to, new BigDecimal("1001")))
        .hasMessageContaining("Insufficient");
    verifyNoInteractions(crypto, conversion);
    verify(fiat, never()).rate(anyString(), anyString());
    verify(bank, never()).adjust(any());
    verify(wallet, never()).adjust(any());
  }

  @Test
  void badQuantityAndFiatOnlyRejected() {
    for (String q : List.of("0", "-1"))
      assertThatThrownBy(() -> trade.trade(email, "EUR", "BTC", new BigDecimal(q)))
          .hasMessageContaining("greater than zero");
    assertThatThrownBy(() -> trade.trade(email, "EUR", "USD", BigDecimal.ONE))
        .hasMessageContaining("currency-conversion");
  }

  @Test
  void rateRetryRecoversBeforeMutation() {
    when(crypto.rate("EUR", "BTC"))
        .thenThrow(BusinessException.unavailable("offline"))
        .thenReturn(new Api.Rate("EUR", "BTC", BigDecimal.ONE, "mock"));
    assertThat(trade.trade(email, "EUR", "BTC", BigDecimal.ONE).received())
        .isEqualByComparingTo("1");
    verify(crypto, times(2)).rate("EUR", "BTC");
    verify(bank, times(1)).adjust(any());
  }

  @Test
  void exhaustedRetryLeavesBalancesUntouched() {
    when(crypto.rate(anyString(), anyString())).thenThrow(BusinessException.unavailable("offline"));
    assertThatThrownBy(() -> trade.trade(email, "EUR", "BTC", BigDecimal.ONE))
        .hasMessageContaining("3 attempts");
    verify(crypto, times(3)).rate("EUR", "BTC");
    verify(bank, never()).adjust(any());
    verify(wallet, never()).adjust(any());
  }

  @Test
  void failedCreditReversesDebitAndCreditInReverseOrder() {
    when(wallet.adjust(any())).thenThrow(BusinessException.unavailable("offline"));
    assertThatThrownBy(() -> trade.trade(email, "EUR", "BTC", BigDecimal.ONE))
        .isInstanceOf(BusinessException.class);
    var order = inOrder(wallet, bank);
    order.verify(wallet).undo(endsWith(":wallet"));
    order.verify(bank).undo(endsWith(":bank"));
  }

  @Test
  void failedBridgedPurchaseAlsoReversesConversion() {
    when(wallet.adjust(any())).thenThrow(BusinessException.unavailable("offline"));
    assertThatThrownBy(() -> trade.trade(email, "RSD", "BTC", BigDecimal.ONE))
        .isInstanceOf(BusinessException.class);
    verify(bank).undo(endsWith(":pre-conversion"));
  }

  @Test
  void failedBridgedSaleReversesAllOperations() {
    doThrow(BusinessException.unavailable("offline")).when(conversion).convert(any());
    assertThatThrownBy(() -> trade.trade(email, "ETH", "RSD", BigDecimal.ONE))
        .isInstanceOf(BusinessException.class);
    verify(bank).undo(endsWith(":post-conversion"));
    verify(bank).undo(endsWith(":bank"));
    verify(wallet).undo(endsWith(":wallet"));
  }

  @Test
  void failedCompensationReportsRecoveryId() {
    when(wallet.adjust(any())).thenThrow(BusinessException.unavailable("offline"));
    doThrow(BusinessException.unavailable("offline")).when(bank).undo(anyString());
    assertThatThrownBy(() -> trade.trade(email, "EUR", "BTC", BigDecimal.ONE))
        .hasMessageContaining("administrator recovery");
  }

  @Test
  void rolesMatchSpecification() {
    var controller = new TradeController(trade);
    for (Role role : Role.values()) {
      var req = new MockHttpServletRequest();
      req.addHeader("X-User-Email", email);
      req.addHeader("X-User-Role", role.name());
      if (role == Role.USER)
        assertThat(controller.trade("EUR", "BTC", BigDecimal.ONE, req)).isNotNull();
      else
        assertThatThrownBy(() -> controller.trade("EUR", "BTC", BigDecimal.ONE, req))
            .isInstanceOf(BusinessException.class);
    }
  }
}
