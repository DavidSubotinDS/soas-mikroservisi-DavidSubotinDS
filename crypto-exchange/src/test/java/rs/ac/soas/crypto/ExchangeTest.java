package rs.ac.soas.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import rs.ac.soas.util.*;

class ExchangeTest {
  RateProvider provider = mock(RateProvider.class);
  ExchangeService exchange = new ExchangeService(provider);

  @Test
  void successfulNormalizedLookup() {
    when(provider.rate("BTC", "ETH")).thenReturn(new BigDecimal("117.5"));
    assertThat(exchange.rate("btc", "ETH").rate()).isEqualByComparingTo("117.5");
  }

  @Test
  void equalCurrencyIsOneWithoutProvider() {
    assertThat(exchange.rate("BTC", "BTC").rate()).isEqualByComparingTo("1");
    verifyNoInteractions(provider);
  }

  @Test
  void unsupportedRejectedBeforeProvider() {
    assertThatThrownBy(() -> exchange.rate("INVALID", "ETH")).isInstanceOf(BusinessException.class);
    verifyNoInteractions(provider);
  }

  @Test
  void providerFailureIsClean() {
    when(provider.rate(anyString(), anyString()))
        .thenThrow(BusinessException.unavailable("Provider unavailable"));
    assertThatThrownBy(() -> exchange.rate("BTC", "ETH")).hasMessage("Provider unavailable");
  }

  @Test
  void invalidProviderRateIsRejected() {
    when(provider.rate(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
    assertThatThrownBy(() -> exchange.rate("BTC", "ETH")).isInstanceOf(BusinessException.class);
  }

  @Test
  void majorCryptoAndSettlementSupport() {
    when(provider.rate(anyString(), anyString())).thenReturn(BigDecimal.TEN);
    for (String asset :
        java.util.List.of("BTC", "ETH", "SOL", "ADA", "XRP", "LTC", "DOGE", "BCH")) {
      assertThat(exchange.rate(asset, "USD").rate()).isPositive();
      assertThat(exchange.rate("EUR", asset).rate()).isPositive();
    }
  }
}
