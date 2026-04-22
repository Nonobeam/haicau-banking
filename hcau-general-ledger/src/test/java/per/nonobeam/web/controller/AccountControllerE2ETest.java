package per.nonobeam.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.ResponseSpec;
import per.nonobeam.common.account.AccountResponse;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.EnableCurrencyRequest;
import per.nonobeam.common.account.LedgerAccountRequest;
import per.nonobeam.common.provider.AbstractProviderAccountPort;
import per.nonobeam.common.provider.ProvisionedAccount;
import per.nonobeam.web.TestcontainersConfiguration;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.UserType;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.BalanceSnapshotRepository;
import per.nonobeam.web.repository.CurrencyRepository;
import per.nonobeam.web.repository.UserProviderAccountRepository;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.UserTypeRepository;
import per.nonobeam.web.repository.WalletRepository;
import per.nonobeam.web.testutil.CurrencyTestFixtures;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccountControllerE2ETest {

  @LocalServerPort private int port;

  @Autowired private UserRepository userRepository;
  @Autowired private UserTypeRepository userTypeRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private WalletRepository walletRepository;
  @Autowired private BalanceSnapshotRepository balanceSnapshotRepository;
  @Autowired private UserProviderAccountRepository userProviderAccountRepository;
  @Autowired private CurrencyRepository currencyRepository;

  @MockitoBean private AbstractProviderAccountPort providerAccountPort;

  private RestClient restClient;

  @BeforeEach
  void setupClient() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    CurrencyTestFixtures.ensureUsd(currencyRepository);
    CurrencyTestFixtures.ensureEur(currencyRepository);
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

  @Test
  void postInternalV1Accounts_returnsCreatedAccount_andPersistsFullWalletStructure() {
    User owner = seedUser();
    String fakeStripeId =
        "acct_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", fakeStripeId));

    ResponseEntity<AccountResponse> response =
        postAccount(new LedgerAccountRequest(owner.getId(), List.of("USD")))
            .toEntity(AccountResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    AccountResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.id()).isNotNull();
    assertThat(body.ownerId()).isEqualTo(owner.getId());
    assertThat(body.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(body.walletId()).isNotNull();
    assertThat(body.coaPath()).contains("wallet:" + owner.getId()).endsWith(":main");

    assertThat(
            accountRepository.findAll().stream()
                .filter(a -> owner.getId().equals(a.getOwner().getId()))
                .count())
        .as("3 SUB + 3 GL mirrors + 2 GL system = 8")
        .isEqualTo(8);
    assertThat(ownerSnapshotsCount(owner)).isEqualTo(8);
    assertThat(walletRepository.findByCustomerId(owner.getId())).isPresent();
    assertThat(userProviderAccountRepository.findAll())
        .singleElement()
        .satisfies(
            mapping -> {
              assertThat(mapping.getProviderCode()).isEqualTo("stripe");
              assertThat(mapping.getExternalAccountId()).isEqualTo(fakeStripeId);
            });
  }

  @Test
  void postInternalV1Accounts_calledTwice_isIdempotent() {
    User owner = seedUser();
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", "acct_test_idem"));
    LedgerAccountRequest request = new LedgerAccountRequest(owner.getId(), List.of("USD"));

    AccountResponse first = postAccount(request).body(AccountResponse.class);
    AccountResponse second = postAccount(request).body(AccountResponse.class);

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.walletId()).isEqualTo(first.walletId());
    assertThat(walletRepository.findAll()).hasSize(1);
  }

  @Test
  void postInternalV1Accounts_unknownUser_returns404() {
    ResponseEntity<String> response =
        restClient
            .post()
            .uri("/internal/v1/accounts")
            .body(new LedgerAccountRequest("user_does_not_exist", List.of("USD")))
            .retrieve()
            .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (req, resp) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("E_101_404_002");
  }

  @Test
  void createAccount_multiCurrency_seedsAllSnapshots() {
    User owner = seedUser();
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", "acct_test_multi"));

    postAccount(new LedgerAccountRequest(owner.getId(), List.of("USD", "EUR"))).toBodilessEntity();

    assertThat(ownerSnapshotsCount(owner)).isEqualTo(16);
  }

  @Test
  void createAccount_emptyCurrencies_returns400() {
    User owner = seedUser();

    ResponseEntity<String> response =
        restClient
            .post()
            .uri("/internal/v1/accounts")
            .body(new LedgerAccountRequest(owner.getId(), List.of()))
            .retrieve()
            .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(), (req, resp) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createAccount_unknownCurrency_returns400() {
    User owner = seedUser();
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", "acct_test_unknown"));

    ResponseEntity<String> response =
        restClient
            .post()
            .uri("/internal/v1/accounts")
            .body(new LedgerAccountRequest(owner.getId(), List.of("XYZ")))
            .retrieve()
            .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(), (req, resp) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("E_101_400_006");
  }

  @Test
  void createAccount_duplicateCurrency_returns400() {
    User owner = seedUser();
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", "acct_test_dup"));

    ResponseEntity<String> response =
        restClient
            .post()
            .uri("/internal/v1/accounts")
            .body(new LedgerAccountRequest(owner.getId(), List.of("USD", "USD")))
            .retrieve()
            .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(), (req, resp) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("E_101_400_007");
  }

  @Test
  void enableCurrency_happyPath_returnsOk() {
    User owner = seedUser();
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", "acct_test_enable"));
    postAccount(new LedgerAccountRequest(owner.getId(), List.of("USD"))).toBodilessEntity();

    ResponseEntity<Void> response =
        restClient
            .post()
            .uri("/internal/v1/accounts/currencies")
            .body(new EnableCurrencyRequest(owner.getId(), "EUR"))
            .retrieve()
            .toBodilessEntity();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    long eurCount =
        balanceSnapshotRepository.findAll().stream()
            .filter(bs -> "EUR".equals(bs.getId().getCurrency()))
            .filter(
                bs ->
                    accountRepository
                        .findById(bs.getId().getAccountId())
                        .map(a -> owner.getId().equals(a.getOwner().getId()))
                        .orElse(false))
            .count();
    assertThat(eurCount).isEqualTo(8);
  }

  @Test
  void enableCurrency_unknownCurrency_returns400() {
    User owner = seedUser();
    when(providerAccountPort.createProviderAccount(anyString()))
        .thenReturn(new ProvisionedAccount("stripe", "acct_test_enable2"));
    postAccount(new LedgerAccountRequest(owner.getId(), List.of("USD"))).toBodilessEntity();

    ResponseEntity<String> response =
        restClient
            .post()
            .uri("/internal/v1/accounts/currencies")
            .body(new EnableCurrencyRequest(owner.getId(), "XYZ"))
            .retrieve()
            .onStatus(status -> status.value() == HttpStatus.BAD_REQUEST.value(), (req, resp) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void enableCurrency_unknownUser_returns404() {
    ResponseEntity<String> response =
        restClient
            .post()
            .uri("/internal/v1/accounts/currencies")
            .body(new EnableCurrencyRequest("user_does_not_exist", "USD"))
            .retrieve()
            .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (req, resp) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void getCurrencies_returnsSeededUsd() {
    ResponseEntity<Object[]> response =
        restClient.get().uri("/internal/v1/currencies").retrieve().toEntity(Object[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotEmpty();
  }

  private long ownerSnapshotsCount(User owner) {
    return balanceSnapshotRepository.findAll().stream()
        .filter(
            bs ->
                accountRepository
                    .findById(bs.getId().getAccountId())
                    .map(a -> owner.getId().equals(a.getOwner().getId()))
                    .orElse(false))
        .count();
  }

  private ResponseSpec postAccount(LedgerAccountRequest request) {
    return restClient.post().uri("/internal/v1/accounts").body(request).retrieve();
  }

  private User seedUser() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserType userType =
        userTypeRepository.findAll().stream()
            .filter(t -> "USER".equals(t.getName()))
            .findFirst()
            .orElseGet(
                () ->
                    userTypeRepository.save(
                        UserType.builder().name("USER").description("End users").build()));
    return userRepository.save(
        User.builder().name("e2e_user_" + suffix).userType(userType).build());
  }
}
