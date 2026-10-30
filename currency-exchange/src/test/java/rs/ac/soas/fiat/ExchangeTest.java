package rs.ac.soas.fiat;

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
    when(provider.rate("EUR", "RSD")).thenReturn(new BigDecimal("117.5"));
    assertThat(exchange.rate("eur", "RSD").rate()).isEqualByComparingTo("117.5");
  }

  @Test
  void equalCurrencyIsOneWithoutProvider() {
    assertThat(exchange.rate("EUR", "EUR").rate()).isEqualByComparingTo("1");
    verifyNoInteractions(provider);
  }

  @Test
  void unsupportedRejectedBeforeProvider() {
    assertThatThrownBy(() -> exchange.rate("INVALID", "RSD")).isInstanceOf(BusinessException.class);
    verifyNoInteractions(provider);
  }

  @Test
  void providerFailureIsClean() {
    when(provider.rate(anyString(), anyString()))
        .thenThrow(BusinessException.unavailable("Provider unavailable"));
    assertThatThrownBy(() -> exchange.rate("EUR", "RSD")).hasMessage("Provider unavailable");
  }

  @Test
  void invalidProviderRateIsRejected() {
    when(provider.rate(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
    assertThatThrownBy(() -> exchange.rate("EUR", "RSD")).isInstanceOf(BusinessException.class);
  }
}
