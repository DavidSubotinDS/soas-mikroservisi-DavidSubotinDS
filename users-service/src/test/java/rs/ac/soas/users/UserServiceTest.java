package rs.ac.soas.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.*;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "soas.demo-data=false"
    })
class UserServiceTest {
  @Test
  void pendingDemoProvisioningResumesAfterDiscoveryRecovers() {
    var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    users.saveAndFlush(new UserEntity("user@soas.local", encoder.encode("User123!"), Role.USER));
    assertThatThrownBy(
            () -> service.authenticate(new Api.Credentials("user@soas.local", "User123!")))
        .hasMessageContaining("in progress");
    service.ensureDemoUserReady();
    verify(bank).provision("user@soas.local");
    verify(wallet).provision("user@soas.local");
    assertThat(service.authenticate(new Api.Credentials("user@soas.local", "User123!")).role())
        .isEqualTo(Role.USER);
  }

  @Autowired UserService service;
  @Autowired UserRepository users;
  @MockitoBean BankClient bank;
  @MockitoBean WalletClient wallet;

  @BeforeEach
  void setup() {
    users.deleteAll();
    reset(bank, wallet);
    when(bank.account(anyString())).thenAnswer(i -> new Api.Account(i.getArgument(0), List.of()));
    when(wallet.account(anyString())).thenAnswer(i -> new Api.Account(i.getArgument(0), List.of()));
  }

  Api.Identity create(Role role) {
    return service.create(
        new Api.UserInput(role.name().toLowerCase() + "@test.local", "Password1!", role),
        Role.OWNER);
  }

  @Test
  void userProvisioningAndAuthentication() {
    var user = create(Role.USER);
    verify(bank).provision(user.email());
    verify(wallet).provision(user.email());
    assertThat(users.findById(user.id()).orElseThrow().password).startsWith("$2");
    assertThat(service.authenticate(new Api.Credentials(user.email(), "Password1!")))
        .isEqualTo(user);
    assertThatThrownBy(() -> service.authenticate(new Api.Credentials(user.email(), "wrong")))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void ownerCanCreateAdmin() {
    assertThat(create(Role.ADMIN).role()).isEqualTo(Role.ADMIN);
    verifyNoInteractions(bank, wallet);
  }

  @Test
  void secondOwnerRejectedOnCreateAndUpdate() {
    create(Role.OWNER);
    var admin = create(Role.ADMIN);
    assertThatThrownBy(
            () ->
                service.create(
                    new Api.UserInput("other@test.local", "Password1!", Role.OWNER), Role.OWNER))
        .hasMessageContaining("Only one OWNER");
    assertThatThrownBy(
            () ->
                service.update(
                    admin.id(), new Api.UserUpdate(admin.email(), null, Role.OWNER), Role.OWNER))
        .hasMessageContaining("Only one OWNER");
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"ADMIN", "OWNER"})
  void adminCannotEscalate(Role role) {
    assertThatThrownBy(
            () ->
                service.create(
                    new Api.UserInput("test@test.local", "Password1!", role), Role.ADMIN))
        .isInstanceOf(BusinessException.class);
    var user = create(Role.USER);
    assertThatThrownBy(
            () ->
                service.update(user.id(), new Api.UserUpdate(user.email(), null, role), Role.ADMIN))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void adminCanCreateAndUpdateUserButNotDelete() {
    var user =
        service.create(new Api.UserInput("test@test.local", "Password1!", Role.USER), Role.ADMIN);
    assertThat(
            service
                .update(user.id(), new Api.UserUpdate(user.email(), null, Role.USER), Role.ADMIN)
                .role())
        .isEqualTo(Role.USER);
    assertThatThrownBy(() -> service.delete(user.id(), Role.ADMIN))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void userHasNoManagementAccess() {
    assertThatThrownBy(() -> service.all(Role.USER)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    new Api.UserInput("a@test.local", "Password1!", Role.USER), Role.USER))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void duplicateEmailRejectedIgnoringCase() {
    create(Role.USER);
    assertThatThrownBy(
            () ->
                service.create(
                    new Api.UserInput("USER@test.local", "Password1!", Role.USER), Role.OWNER))
        .hasMessageContaining("Email already exists");
  }

  @Test
  void deleteCleansBothBeforeRemovingUser() {
    var user = create(Role.USER);
    service.delete(user.id(), Role.OWNER);
    verify(bank).delete(user.email());
    verify(wallet).delete(user.email());
    assertThat(users.findById(user.id())).isEmpty();
  }

  @Test
  void emailChangeMovesOwnership() {
    var user = create(Role.USER);
    service.update(
        user.id(), new Api.UserUpdate("renamed@test.local", null, Role.USER), Role.OWNER);
    verify(bank).rename(new Api.Rename(user.email(), "renamed@test.local"));
    verify(wallet).rename(new Api.Rename(user.email(), "renamed@test.local"));
    assertThat(service.lookup("renamed@test.local").id()).isEqualTo(user.id());
  }

  @Test
  void roleChangesDeleteAndReprovision() {
    var user = create(Role.USER);
    service.update(user.id(), new Api.UserUpdate(user.email(), null, Role.ADMIN), Role.OWNER);
    verify(bank).delete(user.email());
    verify(wallet).delete(user.email());
    service.update(user.id(), new Api.UserUpdate(user.email(), null, Role.USER), Role.OWNER);
    verify(bank, times(2)).provision(user.email());
    verify(wallet, times(2)).provision(user.email());
  }

  @Test
  void failedProvisioningCleansUp() {
    when(wallet.provision(anyString())).thenThrow(BusinessException.unavailable("offline"));
    assertThatThrownBy(() -> create(Role.USER)).hasMessageContaining("creation failed");
    verify(bank).delete("user@test.local");
    verify(wallet).delete("user@test.local");
    assertThat(users.count()).isZero();
  }

  @Test
  void failedDeletionRestoresSnapshots() {
    var user = create(Role.USER);
    doThrow(BusinessException.unavailable("offline")).when(wallet).delete(anyString());
    assertThatThrownBy(() -> service.delete(user.id(), Role.OWNER))
        .hasMessageContaining("restored");
    assertThat(users.findById(user.id())).isPresent();
    verify(bank).restore(any());
    verify(wallet).restore(any());
  }

  @Test
  void failedRenameRestoresOldOwnership() {
    var user = create(Role.USER);
    doThrow(BusinessException.unavailable("offline")).when(wallet).rename(any());
    assertThatThrownBy(
            () ->
                service.update(
                    user.id(), new Api.UserUpdate("new@test.local", null, Role.USER), Role.OWNER))
        .hasMessageContaining("restored");
    assertThat(service.lookup(user.email()).id()).isEqualTo(user.id());
    verify(bank).restore(any());
    verify(wallet).restore(any());
  }
}
