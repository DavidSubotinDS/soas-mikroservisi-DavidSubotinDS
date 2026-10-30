package rs.ac.soas.bank;

import jakarta.persistence.*;

@Entity
@Table(name = "operations")
public class LedgerEntry {
  @Id String operationId;

  @Column(nullable = false)
  String email;

  @Column(nullable = false, length = 4000)
  String deltas;

  @Column(nullable = false)
  boolean undone;

  protected LedgerEntry() {}

  LedgerEntry(String operationId, String email, String deltas) {
    this.operationId = operationId;
    this.email = email;
    this.deltas = deltas;
  }
}
