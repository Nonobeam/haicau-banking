package per.nonobeam.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.BaseIntegrationTest;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.UserType;
import per.nonobeam.web.model.account.AccountResponse;
import per.nonobeam.web.model.account.CreateAccountRequest;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.UserTypeRepository;
import per.nonobeam.web.repository.WalletProvisioningStatusRepository;

class AccountServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private AccountService accountService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserTypeRepository userTypeRepository;
  @Autowired private WalletProvisioningStatusRepository walletProvisioningStatusRepository;

  private User testOwner;
  private User systemUser;

  @AfterEach
  void cleanup() {
    accountRepository.deleteAll();
    walletProvisioningStatusRepository.deleteAll();
    userRepository.deleteAll();
    userTypeRepository.deleteAll();
  }

  private void seedTestData() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    UserType userType =
        userTypeRepository.save(UserType.builder().name("USER").description("End users").build());

    UserType systemType =
        userTypeRepository.save(
            UserType.builder().name("SYSTEM").description("System accounts").build());

    testOwner =
        userRepository.save(User.builder().name("test_user_" + suffix).userType(userType).build());

    systemUser = userRepository.save(User.builder().name("system").userType(systemType).build());
  }

  @Test
  @Transactional
  void createAccount_validUser_provisionsWalletWithFiveAccounts() {
    seedTestData();

    AccountResponse response =
        accountService.createAccount(
            CreateAccountRequest.builder().ownerId(testOwner.getId()).build());

    assertThat(response).isNotNull();
    assertThat(response.getId()).isNotNull();
    assertThat(response.getOwnerId()).isEqualTo(testOwner.getId());
    assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.getCoaPath()).contains("wallet:" + testOwner.getId()).endsWith(":main");
    assertThat(response.getWalletId()).isNotNull();

    // 3 SUB (main, reserved, clearing) + 2 GL (offset, revenue) = 5
    long accountCount =
        accountRepository.findAll().stream()
            .filter(a -> testOwner.getId().equals(a.getOwner().getId()))
            .count();
    assertThat(accountCount).isEqualTo(5);

    // provisioning status recorded
    assertThat(walletProvisioningStatusRepository.existsById(testOwner.getId())).isTrue();
  }

  @Test
  @Transactional
  void createAccount_existingUser_isIdempotent() {
    seedTestData();
    CreateAccountRequest request =
        CreateAccountRequest.builder().ownerId(testOwner.getId()).build();

    AccountResponse first = accountService.createAccount(request);
    AccountResponse second = accountService.createAccount(request);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(second.getWalletId()).isEqualTo(first.getWalletId());
  }

  @Test
  void createAccount_systemUser_throwsUserNotFound() {
    seedTestData();

    assertThatThrownBy(
            () ->
                accountService.createAccount(
                    CreateAccountRequest.builder().ownerId(systemUser.getId()).build()))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void createAccount_unknownUser_throwsUserNotFound() {
    assertThatThrownBy(
            () ->
                accountService.createAccount(
                    CreateAccountRequest.builder().ownerId("user_nonexistent").build()))
        .isInstanceOf(ApplicationException.class);
  }
}
