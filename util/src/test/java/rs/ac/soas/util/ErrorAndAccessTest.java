package rs.ac.soas.util;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ErrorAndAccessTest {
  @Test
  void errorsHavePredictableShapeAndNoStackTrace() {
    var request = new MockHttpServletRequest("GET", "/trade-service");
    var response =
        new GlobalErrors().business(BusinessException.bad("Insufficient funds"), request);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().message()).isEqualTo("Insufficient funds");
    assertThat(response.getBody().path()).isEqualTo("/trade-service");
    assertThat(ApiError.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("trace", "stackTrace");
  }

  @Test
  void amountsAndAssetsAreValidated() {
    assertThat(Checks.fiat(" eur ")).isEqualTo("EUR");
    assertThatThrownBy(() -> Checks.crypto("EUR")).hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> Checks.positive(new BigDecimal("0.0000000000001")))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Checks.nonnegative(new BigDecimal("-1")))
        .hasMessageContaining("negative");
    assertThatThrownBy(() -> Checks.nonnegative(new BigDecimal("1E+26")))
        .hasMessageContaining("26 integer digits");
    assertThat(Checks.nonnegative(new BigDecimal("99999999999999999999999999")))
        .isEqualByComparingTo("99999999999999999999999999");
  }

  @Test
  void forgedOrMissingIdentityCannotAccessBusinessLogic() {
    var req = new MockHttpServletRequest();
    assertThatThrownBy(() -> Access.role(req)).isInstanceOf(BusinessException.class);
  }
}
