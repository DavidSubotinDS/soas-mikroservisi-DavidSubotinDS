package rs.ac.soas.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import rs.ac.soas.contract.FiatClient;

class FiatCurrenciesTest {
  @Test
  void validatesAgainstFeignCatalogAndCachesSuccessfulResponses() {
    var exchange = mock(FiatClient.class);
    when(exchange.currencies()).thenReturn(List.of("EUR", "NZD"));
    var currencies = new FiatCurrencies(exchange);
    assertThat(currencies.require(" nzd ")).isEqualTo("NZD");
    assertThat(currencies.require("EUR")).isEqualTo("EUR");
    assertThatThrownBy(() -> currencies.require("ZZZ")).hasMessageContaining("Unsupported");
    verify(exchange, times(1)).currencies();
  }

  @Test
  void failedCatalogDoesNotCacheAnEmptyAllowlistAndCanRecover() {
    var exchange = mock(FiatClient.class);
    when(exchange.currencies()).thenReturn(List.of(), List.of("NZD"));
    var currencies = new FiatCurrencies(exchange);
    assertThatThrownBy(() -> currencies.require("NZD"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("catalog");
    assertThat(currencies.require("NZD")).isEqualTo("NZD");
  }
}
