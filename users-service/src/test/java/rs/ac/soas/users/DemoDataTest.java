package rs.ac.soas.users;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import rs.ac.soas.contract.*;
import rs.ac.soas.util.BusinessException;

class DemoDataTest {
  UserService users = mock(UserService.class);
  BankClient bank = mock(BankClient.class);
  WalletClient wallet = mock(WalletClient.class);
  DemoData demo = new DemoData(users, bank, wallet);

  @Test
  void waitsForBothServicesBeforeCreatingAndFundsOnlyOnce() {
    when(wallet.account(anyString())).thenThrow(BusinessException.unavailable("not discovered"));
    demo.demoUser();
    verifyNoInteractions(users);
    verify(bank, never()).adjust(any());
    doReturn(new Api.Account("user@soas.local", java.util.List.of()))
        .when(wallet)
        .account(anyString());
    demo.demoUser();
    demo.demoUser();
    verify(users).ensureDemoUserReady();
    verify(bank).adjust(argThat(input -> input.operationId().equals("demo-bank-funding")));
    verify(wallet).adjust(argThat(input -> input.operationId().equals("demo-wallet-funding")));
  }

  @Test
  void retryUsesTheSameFundingIdsAfterPartialFailure() {
    when(wallet.adjust(any())).thenThrow(BusinessException.unavailable("offline")).thenReturn(null);
    demo.demoUser();
    demo.demoUser();
    demo.demoUser();
    verify(bank, times(2))
        .adjust(argThat(input -> input.operationId().equals("demo-bank-funding")));
    verify(wallet, times(2))
        .adjust(argThat(input -> input.operationId().equals("demo-wallet-funding")));
  }
}
