package rs.ac.soas.users;

import jakarta.persistence.*;
import rs.ac.soas.contract.*;

@Entity
@Table(name = "app_users")
public class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true)
  String email;

  @Column(nullable = false)
  String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  Role role;

  @Column(unique = true)
  String ownerSlot;

  @Column(nullable = false)
  boolean ready;

  protected UserEntity() {}

  UserEntity(String email, String password, Role role) {
    this.email = email;
    this.password = password;
    this.ready = role != Role.USER;
    setRole(role);
  }

  void setRole(Role role) {
    this.role = role;
    this.ownerSlot = role == Role.OWNER ? "OWNER" : null;
  }

  Api.Identity dto() {
    return new Api.Identity(id, email, role);
  }
}
