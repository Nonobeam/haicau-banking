package per.nonobeam.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.AccountResponse;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.EnableCurrencyRequest;
import per.nonobeam.common.account.LedgerAccountRequest;
import per.nonobeam.common.provider.AbstractProviderAccountPort;
import per.nonobeam.common.provider.ProvisionedAccount;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.BaseIntegrationTest;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.UserType;
import per.nonobeam.web.common.ledger.BalanceSnapshot;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.BalanceSnapshotRepository;
import per.nonobeam.web.repository.CurrencyRepository;
import per.nonobeam.web.repository.UserProviderAccountRepository;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.UserTypeRepository;
import per.nonobeam.web.repository.WalletRepository;
import per.nonobeam.web.testutil.CurrencyTestFixtures;

class AccountServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private AccountService accountService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserTypeRepository userTypeRepository;
  @Autowired private BalanceSnapshotRepository balanceSnapshotRepository;
  @Autowired private CurrencyRepository currencyRepository;
  @Autowired private WalletRepository walletRepository;
  @Autowired private UserProviderAccountRepository userProviderAccountRepository;
  @MockitoBean private AbstractProviderAccountPort providerAccountPort;

  @BeforeEach
  void seedCurrencies() {
    CurrencyTestFixtures.ensureUsd(currencyRepository);
  }

  @AfterEach
  void cleanup() {
    balanceSnapshotRepository.deleteAll();
    accountRepository.deleteAll();
    userProviderAccountRepository.deleteAll();
    walletRepository.deleteAll();
    userRepository.deleteAll();
    userTypeRepository.deleteAll();
  }

  private record Seed(User owner, User system) {}

  private Seed seedTestData() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserType userType =
        userTypeRepository.save(UserType.builder().name("USER").description("End users").build());
    UserType systemType =
        userTypeRepository.save(
            UserType.builder().name("SYSTEM").description("System accounts").build());
    User owner =
        userRepository.save(User.builder().name("test_user_" + suffix).userType(userType).build());
    User system = userRepository.save(User.builder().name("system").userType(systemType).build());
    return new Seed(owner, system);
  }

  private void mockProvider() {
    String fakeId = "acct_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", fakeId));
  }

  @Test
  @Transactional
  void provisionAccount_validUser_provisionsWalletWithEightAccounts() {
    Seed seed = seedTestData();
    mockProvider();

    AccountResponse response =
        accountService.provisionAccount(
            new LedgerAccountRequest(seed.owner().getId(), List.of("USD")));

    assertThat(response).isNotNull();
    assertThat(response.id()).isNotNull();
    assertThat(response.ownerId()).isEqualTo(seed.owner().getId());
    assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.coaPath()).contains("wallet:" + seed.owner().getId()).endsWith(":main");
    assertThat(response.walletId()).isNotNull();

    long accountCount =
        accountRepository.findAll().stream()
            .filter(a -> seed.owner().getId().equals(a.getOwner().getId()))
            .count();
    assertThat(accountCount).isEqualTo(8);
  }

  @Test
  @Transactional
  void provisionAccount_calledTwice_isIdempotent() {
    Seed seed = seedTestData();
    mockProvider();
    LedgerAccountRequest request = new LedgerAccountRequest(seed.owner().getId(), List.of("USD"));

    AccountResponse first = accountService.provisionAccount(request);
    AccountResponse second = accountService.provisionAccount(request);

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.walletId()).isEqualTo(first.walletId());
  }

  @Test
  void provisionAccount_systemUser_throwsUserNotFound() {
    Seed seed = seedTestData();
    assertThatThrownBy(
            () ->
                accountService.provisionAccount(
                    new LedgerAccountRequest(seed.system().getId(), List.of("USD"))))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void provisionAccount_unknownUser_throwsUserNotFound() {
    assertThatThrownBy(
            () ->
                accountService.provisionAccount(
                    new LedgerAccountRequest("user_nonexistent", List.of("USD"))))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  @Transactional
  void provisionAccount_twoCurrencies_writes16Snapshots() {
    Seed seed = seedTestData();
    mockProvider();
    CurrencyTestFixtures.ensureEur(currencyRepository);

    accountService.provisionAccount(
        new LedgerAccountRequest(seed.owner().getId(), List.of("USD", "EUR")));

    List<BalanceSnapshot> snapshots =
        balanceSnapshotRepository.findAll().stream()
            .filter(
                bs ->
                    accountRepository
                        .findById(bs.getId().getAccountId())
                        .map(a -> seed.owner().getId().equals(a.getOwner().getId()))
                        .orElse(false))
            .toList();
    assertThat(snapshots).hasSize(16);
    assertThat(snapshots.stream().map(bs -> bs.getId().getCurrency()).distinct())
        .containsExactlyInAnyOrder("USD", "EUR");
  }

  @Test
  @Transactional
  void provisionAccount_nonUsdOnly_writesOnlyRequestedSnapshots() {
    Seed seed = seedTestData();
    mockProvider();
    CurrencyTestFixtures.ensureEur(currencyRepository);

    accountService.provisionAccount(new LedgerAccountRequest(seed.owner().getId(), List.of("EUR")));

    List<BalanceSnapshot> snapshots =
        balanceSnapshotRepository.findAll().stream()
            .filter(
                bs ->
                    accountRepository
                        .findById(bs.getId().getAccountId())
                        .map(a -> seed.owner().getId().equals(a.getOwner().getId()))
                        .orElse(false))
            .toList();
    assertThat(snapshots).hasSize(8);
    assertThat(snapshots).allMatch(bs -> "EUR".equals(bs.getId().getCurrency()));
  }

  @Test
  void provisionAccount_duplicateCurrencies_throwsDuplicateCurrency() {
    Seed seed = seedTestData();
    mockProvider();

    assertThatThrownBy(
            () ->
                accountService.provisionAccount(
                    new LedgerAccountRequest(seed.owner().getId(), List.of("USD", "USD"))))
        .isInstanceOf(ApplicationException.class)
        .extracting(e -> ((ApplicationException) e).getErrorCode())
        .isEqualTo(ApplicationErrorCode.DUPLICATE_CURRENCY);
  }

  @Test
  void provisionAccount_unknownCurrency_throwsCurrencyNotRegistered() {
    Seed seed = seedTestData();
    mockProvider();

    assertThatThrownBy(
            () ->
                accountService.provisionAccount(
                    new LedgerAccountRequest(seed.owner().getId(), List.of("XYZ"))))
        .isInstanceOf(ApplicationException.class)
        .extracting(e -> ((ApplicationException) e).getErrorCode())
        .isEqualTo(ApplicationErrorCode.CURRENCY_NOT_REGISTERED);
  }

  @Test
  @Transactional
  void enableCurrency_runTwice_secondIsNoOp() {
    Seed seed = seedTestData();
    mockProvider();
    CurrencyTestFixtures.ensureEur(currencyRepository);
    accountService.provisionAccount(new LedgerAccountRequest(seed.owner().getId(), List.of("USD")));

    accountService.enableCurrency(new EnableCurrencyRequest(seed.owner().getId(), "EUR"));
    long afterFirst = balanceSnapshotRepository.count();
    accountService.enableCurrency(new EnableCurrencyRequest(seed.owner().getId(), "EUR"));
    long afterSecond = balanceSnapshotRepository.count();

    assertThat(afterSecond).isEqualTo(afterFirst);
  }

  @Test
  @Transactional
  void enableCurrency_addsEurToUsdOnlyUser() {
    Seed seed = seedTestData();
    mockProvider();
    CurrencyTestFixtures.ensureEur(currencyRepository);
    accountService.provisionAccount(new LedgerAccountRequest(seed.owner().getId(), List.of("USD")));

    accountService.enableCurrency(new EnableCurrencyRequest(seed.owner().getId(), "EUR"));

    long eurCount =
        balanceSnapshotRepository.findAll().stream()
            .filter(
                bs ->
                    "EUR".equals(bs.getId().getCurrency())
                        && accountRepository
                            .findById(bs.getId().getAccountId())
                            .map(a -> seed.owner().getId().equals(a.getOwner().getId()))
                            .orElse(false))
            .count();
    assertThat(eurCount).isEqualTo(8);
  }
}
