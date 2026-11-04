package rs.ac.soas.conversion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

class ConversionTest {
  BankClient bank = mock(BankClient.class);
  FiatClient rates = mock(FiatClient.class);
  ConversionService service = new ConversionService(bank, rates, new FiatCurrencies(rates));

  @BeforeEach
  void setup() {
    when(rates.currencies()).thenReturn(List.of("EUR", "RSD", "USD", "NZD"));
    when(bank.account("user@test.local"))
        .thenReturn(
            new Api.Account(
                "user@test.local", List.of(new Api.Holding(1, "EUR", new BigDecimal("1000")))));
    when(rates.rate("EUR", "RSD"))
        .thenReturn(new Api.Rate("EUR", "RSD", new BigDecimal("117"), "mock"));
    when(bank.swap(any()))
        .thenAnswer(
            i -> {
              Api.Swap s = i.getArgument(0);
              return new Api.Account(
                  s.email(),
                  List.of(
                      new Api.Holding(1, "EUR", new BigDecimal("900")),
                      new Api.Holding(2, s.to(), s.received())));
            });
  }

  Api.Transaction convert(String quantity) {
    return service.convert(
        new Api.ConversionRequest(
            "user@test.local", "EUR", "RSD", new BigDecimal(quantity), null, null));
  }

  @Test
  void convertsIntoProviderFiatOutsideOriginalAllowlist() {
    when(rates.rate("EUR", "NZD"))
        .thenReturn(new Api.Rate("EUR", "NZD", new BigDecimal("2"), "mock"));
    var result =
        service.convert(
            new Api.ConversionRequest(
                "user@test.local", "EUR", "NZD", new BigDecimal("10"), null, null));
    assertThat(result.received()).isEqualByComparingTo("20");
    verify(bank).swap(argThat(s -> s.to().equals("NZD")));
  }

  @Test
  void calculatesAndMutatesAtomically() {
    var result = convert("100");
    assertThat(result.received()).isEqualByComparingTo("11700");
    assertThat(result.bankAccount().holdings()).hasSize(2);
    verify(bank)
        .swap(
            argThat(
                s ->
                    s.quantity().compareTo(new BigDecimal("100")) == 0
                        && s.received().compareTo(new BigDecimal("11700")) == 0));
  }

  @Test
  void insufficientFunds() {
    assertThatThrownBy(() -> convert("1001")).hasMessageContaining("Insufficient");
    verify(bank, never()).swap(any());
  }

  @Test
  void positiveQuantityRequired() {
    for (String q : List.of("0", "-2"))
      assertThatThrownBy(() -> convert(q)).hasMessageContaining("greater than zero");
  }

  @Test
  void missingHolding() {
    assertThatThrownBy(
            () ->
                service.convert(
                    new Api.ConversionRequest(
                        "user@test.local", "USD", "EUR", BigDecimal.ONE, null, null)))
        .hasMessageContaining("Missing");
  }

  @Test
  void failedMutationTriggersUndo() {
    doThrow(BusinessException.unavailable("offline")).when(bank).swap(any());
    assertThatThrownBy(() -> convert("1")).isInstanceOf(BusinessException.class);
    verify(bank).undo(anyString());
  }

  @Test
  void onlyUserAllowed() {
    var controller = new ConversionController(service);
    for (Role role : Role.values()) {
      var req = new MockHttpServletRequest();
      req.addHeader("X-User-Role", role.name());
      req.addHeader("X-User-Email", "user@test.local");
      if (role == Role.USER)
        assertThat(controller.convert("EUR", "RSD", BigDecimal.ONE, req)).isNotNull();
      else
        assertThatThrownBy(() -> controller.convert("EUR", "RSD", BigDecimal.ONE, req))
            .isInstanceOf(BusinessException.class);
    }
  }
}
