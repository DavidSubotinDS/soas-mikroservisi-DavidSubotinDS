package rs.ac.soas.contract;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class Api {
  private Api() {}

  public record Identity(long id, String email, Role role) {}

  public record Credentials(@NotBlank String email, @NotBlank String password) {}

  public record UserInput(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 100) String password,
      @NotNull Role role) {}

  public record UserUpdate(
      @NotBlank @Email String email,
      @Size(min = 8, max = 100) String password,
      @NotNull Role role) {}

  public record Holding(long id, String currency, BigDecimal amount) {}

  public record Account(String email, List<Holding> holdings) {}

  public record HoldingInput(
      @NotBlank @Email String email,
      @NotBlank String currency,
      @NotNull @DecimalMin("0") BigDecimal amount) {}

  public record Rate(String from, String to, BigDecimal rate, String provider) {}

  public record Swap(
      String email,
      String from,
      String to,
      BigDecimal quantity,
      BigDecimal received,
      String operationId) {}

  public record Adjustment(String email, String currency, BigDecimal delta, String operationId) {}

  public record Rename(String oldEmail, String newEmail) {}

  public record Transaction(
      String from,
      String to,
      BigDecimal quantity,
      BigDecimal received,
      Account bankAccount,
      Account wallet,
      String report,
      String operationId) {}

  public record ConversionRequest(
      String email,
      String from,
      String to,
      BigDecimal quantity,
      BigDecimal quotedRate,
      String operationId) {}
}
