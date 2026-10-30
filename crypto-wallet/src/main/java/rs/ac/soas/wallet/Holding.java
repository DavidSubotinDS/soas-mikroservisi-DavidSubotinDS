package rs.ac.soas.wallet;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
    name = "holdings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"email", "currency"}))
public class Holding {
  @Version Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String email;

  @Column(nullable = false)
  String currency;

  @Column(nullable = false, precision = 38, scale = 12)
  BigDecimal amount;

  protected Holding() {}

  Holding(String email, String currency, BigDecimal amount) {
    this.email = email;
    this.currency = currency;
    this.amount = amount;
  }
}
