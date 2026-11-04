package rs.ac.soas.fiat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import rs.ac.soas.util.*;

class ExchangeTest {
  RateProvider provider = mock(RateProvider.class);
  ExchangeService exchange = new ExchangeService(provider);

  @BeforeEach
  void catalog() {
    when(provider.currencies()).thenReturn(java.util.List.of("EUR", "RSD", "USD", "NZD"));
  }

  @Test
  void successfulNormalizedLookup() {
    when(provider.rate("EUR", "RSD")).thenReturn(new BigDecimal("117.5"));
    assertThat(exchange.rate("eur", "RSD").rate()).isEqualByComparingTo("117.5");
  }

  @Test
  void equalCurrencyIsOneWithoutRateLookup() {
    assertThat(exchange.rate("EUR", "EUR").rate()).isEqualByComparingTo("1");
    verify(provider, never()).rate(anyString(), anyString());
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

  @Test
  void providerCurrencyOutsideOriginalAllowlistIsSupportedAndCatalogIsCached() {
    when(provider.rate("EUR", "NZD")).thenReturn(new BigDecimal("1.99"));
    assertThat(exchange.rate("EUR", "nzd").rate()).isEqualByComparingTo("1.99");
    assertThat(exchange.currencies()).contains("NZD");
    verify(provider, times(1)).currencies();
  }

  @Test
  void unsupportedThreeLetterCodeCannotBypassProviderCatalog() {
    assertThatThrownBy(() -> exchange.rate("ZZZ", "EUR"))
        .hasMessageContaining("Unsupported fiat currency");
    verify(provider, never()).rate(anyString(), anyString());
  }

  @Test
  void missingCatalogIsUnavailableRatherThanFallingBackToFixedCurrencies() {
    when(provider.currencies()).thenReturn(java.util.List.of());
    assertThatThrownBy(() -> exchange.rate("EUR", "EUR"))
        .hasMessageContaining("catalog is unavailable");
  }
}
