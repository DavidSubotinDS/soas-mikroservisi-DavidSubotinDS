package rs.ac.soas.users;

import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@Service
public class UserService {
  private final UserRepository users;
  private final BankClient bank;
  private final WalletClient wallet;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public UserService(UserRepository users, BankClient bank, WalletClient wallet) {
    this.users = users;
    this.bank = bank;
    this.wallet = wallet;
  }

  public List<Api.Identity> all(Role actor) {
    manage(actor);
    return users.findAll().stream().map(UserEntity::dto).toList();
  }

  public Api.Identity get(long id, Role actor) {
    manage(actor);
    return entity(id).dto();
  }

  public Api.Identity lookup(String email) {
    return users
        .findByEmail(Assets.email(email))
        .orElseThrow(() -> BusinessException.missing("User not found"))
        .dto();
  }

  public Api.Identity authenticate(Api.Credentials credentials) {
    UserEntity user = users.findByEmail(Assets.email(credentials.email())).orElse(null);
    if (user == null || !encoder.matches(credentials.password(), user.password))
      throw new BusinessException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid email or password");
    if (!user.ready)
      throw BusinessException.unavailable("User lifecycle operation is in progress; retry shortly");
    return user.dto();
  }

  public synchronized Api.Identity create(Api.UserInput input, Role actor) {
    manage(actor);
    if (actor == Role.ADMIN && input.role() != Role.USER) throw BusinessException.forbidden();
    String email = Assets.email(input.email());
    unique(email, null);
    owner(input.role(), null);
    UserEntity user =
        users.saveAndFlush(new UserEntity(email, encoder.encode(input.password()), input.role()));
    if (user.role == Role.USER) {
      try {
        bank.provision(email);
        wallet.provision(email);
        user.ready = true;
        users.saveAndFlush(user);
      } catch (RuntimeException failure) {
        compensate(failure, () -> bank.delete(email), () -> wallet.delete(email));
        users.deleteById(user.id);
        throw BusinessException.unavailable(
            "User creation failed; provisioned holdings were removed");
      }
    }
    return user.dto();
  }

  public synchronized Api.Identity update(long id, Api.UserUpdate input, Role actor) {
    manage(actor);
    UserEntity user = entity(id);
    if (actor == Role.ADMIN && (user.role != Role.USER || input.role() != Role.USER))
      throw BusinessException.forbidden();
    String email = Assets.email(input.email());
    unique(email, id);
    owner(input.role(), id);
    String previousEmail = user.email, previousPassword = user.password;
    Role previousRole = user.role;
    boolean wasUser = previousRole == Role.USER,
        becomesUser = input.role() == Role.USER,
        renamed = !previousEmail.equals(email);
    Api.Account bankSnapshot = wasUser ? bank.account(previousEmail) : null,
        walletSnapshot = wasUser ? wallet.account(previousEmail) : null;
    user.ready = false;
    users.saveAndFlush(user);
    try {
      if (wasUser && !becomesUser) {
        bank.delete(previousEmail);
        wallet.delete(previousEmail);
      }
      if (wasUser && becomesUser && renamed) {
        bank.rename(new Api.Rename(previousEmail, email));
        wallet.rename(new Api.Rename(previousEmail, email));
      }
      user.email = email;
      user.setRole(input.role());
      if (input.password() != null && !input.password().isBlank())
        user.password = encoder.encode(input.password());
      users.saveAndFlush(user);
      if (!wasUser && becomesUser) {
        bank.provision(email);
        wallet.provision(email);
      }
      user.ready = true;
      users.saveAndFlush(user);
      return user.dto();
    } catch (RuntimeException failure) {
      if (!wasUser && becomesUser)
        compensate(failure, () -> bank.delete(email), () -> wallet.delete(email));
      if (wasUser) {
        if (renamed) compensate(failure, () -> bank.delete(email), () -> wallet.delete(email));
        compensate(failure, () -> bank.restore(bankSnapshot), () -> wallet.restore(walletSnapshot));
      }
      user.email = previousEmail;
      user.password = previousPassword;
      user.setRole(previousRole);
      user.ready = true;
      users.saveAndFlush(user);
      throw BusinessException.unavailable(
          "User update failed; previous user and holdings were restored");
    }
  }

  public synchronized void delete(long id, Role actor) {
    if (actor != Role.OWNER) throw BusinessException.forbidden();
    UserEntity user = entity(id);
    if (user.role == Role.USER) {
      Api.Account b = bank.account(user.email), w = wallet.account(user.email);
      user.ready = false;
      users.saveAndFlush(user);
      try {
        bank.delete(user.email);
        wallet.delete(user.email);
        users.deleteById(id);
      } catch (RuntimeException failure) {
        compensate(failure, () -> bank.restore(b), () -> wallet.restore(w));
        user.ready = true;
        users.saveAndFlush(user);
        throw BusinessException.unavailable("Deletion failed; user and holdings were restored");
      }
    } else users.deleteById(id);
  }

  private UserEntity entity(long id) {
    return users.findById(id).orElseThrow(() -> BusinessException.missing("User not found"));
  }

  private void unique(String email, Long id) {
    users
        .findByEmail(email)
        .ifPresent(
            u -> {
              if (!u.id.equals(id)) throw BusinessException.conflict("Email already exists");
            });
  }

  private void owner(Role role, Long id) {
    if (role == Role.OWNER
        && users.findAll().stream().anyMatch(u -> u.role == Role.OWNER && !u.id.equals(id)))
      throw BusinessException.conflict("Only one OWNER is allowed");
  }

  private void manage(Role role) {
    if (role != Role.OWNER && role != Role.ADMIN) throw BusinessException.forbidden();
  }

  private void compensate(RuntimeException original, Runnable... actions) {
    boolean failed = false;
    for (Runnable action : actions)
      try {
        action.run();
      } catch (RuntimeException ex) {
        original.addSuppressed(ex);
        failed = true;
      }
    if (failed) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .error("Lifecycle compensation requires recovery", original);
      throw BusinessException.unavailable(
          "Lifecycle cleanup could not finish. User remains locked; restore downstream services and"
              + " contact the administrator");
    }
  }

  public synchronized void seedManagers() {
    if (users.count() != 0) return;
    users.saveAndFlush(new UserEntity("owner@soas.local", encoder.encode("Owner123!"), Role.OWNER));
    users.saveAndFlush(new UserEntity("admin@soas.local", encoder.encode("Admin123!"), Role.ADMIN));
  }

  synchronized Api.Identity ensureDemoUserReady() {
    String email = "user@soas.local";
    UserEntity user = users.findByEmail(email).orElse(null);
    if (user == null) return create(new Api.UserInput(email, "User123!", Role.USER), Role.OWNER);
    if (user.role != Role.USER)
      throw BusinessException.conflict("The demo email belongs to a different role");
    // Only the known demo account is resumed automatically; ordinary lifecycle failures
    // retain their explicit recovery behavior.
    if (!user.ready) {
      bank.provision(email);
      wallet.provision(email);
      user.ready = true;
      users.saveAndFlush(user);
    }
    return user.dto();
  }
}
