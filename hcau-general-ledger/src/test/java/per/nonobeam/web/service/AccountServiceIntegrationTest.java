package per.nonobeam.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.exception.StripeException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.BaseIntegrationTest;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.BucketEnum;
import per.nonobeam.web.common.account.DomainType;
import per.nonobeam.web.common.account.InternalCoaFactory;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.UserType;
import per.nonobeam.web.model.account.AccountResponse;
import per.nonobeam.web.model.account.CreateAccountRequest;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.DomainTypeRepository;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.UserTypeRepository;
import per.nonobeam.web.service.stripe.StripeAccountService;

class AccountServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private AccountService accountService;

  @Autowired private AccountRepository accountRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private DomainTypeRepository domainTypeRepository;

  @Autowired private UserTypeRepository userTypeRepository;

  @MockitoBean private StripeAccountService stripeAccountService;

  private User testOwner;
  private DomainType fiatDomain;
  private DomainType cryptoDomain;

  @AfterEach
  void cleanup() {
    accountRepository.deleteAll();
    userRepository.deleteAll();
    domainTypeRepository.deleteAll();
    userTypeRepository.deleteAll();
  }

  private void seedTestData() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    UserType userType =
        userTypeRepository.save(
            UserType.builder()
                .name("USER_" + suffix)
                .description("End users for integration test")
                .build());

    testOwner =
        userRepository.save(User.builder().name("test_user_" + suffix).userType(userType).build());

    fiatDomain =
        domainTypeRepository.save(
            DomainType.builder().name("FIAT_" + suffix).description("Fiat currencies").build());

    cryptoDomain =
        domainTypeRepository.save(
            DomainType.builder().name("CRYPTO_" + suffix).description("Crypto currencies").build());
  }

  private String stubStripeSuccess() throws StripeException {
    String stripeAccountId = "acct_stripe_" + UUID.randomUUID().toString().substring(0, 8);
    com.stripe.model.v2.core.Account mockStripeAccount =
        mock(com.stripe.model.v2.core.Account.class);
    when(mockStripeAccount.getId()).thenReturn(stripeAccountId);
    when(stripeAccountService.createAccount(anyString(), any())).thenReturn(mockStripeAccount);
    return stripeAccountId;
  }

  @Test
  @Transactional
  void createAccount_validFiatRequest_createsAvailableAndReservedAccountsWithStripe()
      throws Exception {
    // given
    seedTestData();
    String stripeAccountId = stubStripeSuccess();

    CreateAccountRequest request =
        CreateAccountRequest.builder()
            .ownerId(testOwner.getId())
            .domainId(fiatDomain.getId())
            .currency("usd")
            .build();

    // when
    AccountResponse response = accountService.createAccount(request);

    // then — verify response fields
    assertThat(response).isNotNull();
    assertThat(response.getId()).isNotNull();
    assertThat(response.getOwnerId()).isEqualTo(testOwner.getId());
    assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.getStripeAccountId()).isEqualTo(stripeAccountId);
    assertThat(response.getDomain()).isEqualToIgnoringCase("FIAT");
    assertThat(response.getCurrency()).isEqualTo("USD");
    assertThat(response.getBucketType()).isEqualTo(BucketEnum.AVAILABLE.name());

    // then — verify available account persisted in DB
    String expectedAvailableCoa =
        InternalCoaFactory.build(testOwner.getId(), "FIAT", "USD", BucketEnum.AVAILABLE);
    var availableAccount = accountRepository.findByInternalCoa(expectedAvailableCoa);
    assertThat(availableAccount).isPresent();
    assertThat(availableAccount.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(availableAccount.get().getStripeAccountId()).isEqualTo(stripeAccountId);
    assertThat(availableAccount.get().getStripeMetadata()).isNotBlank();

    // then — verify reserved account persisted in DB
    String expectedReservedCoa =
        InternalCoaFactory.build(testOwner.getId(), "FIAT", "USD", BucketEnum.RESERVED);
    var reservedAccount = accountRepository.findByInternalCoa(expectedReservedCoa);
    assertThat(reservedAccount).isPresent();
    assertThat(reservedAccount.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);

    // then — verify Stripe was called exactly once
    verify(stripeAccountService).createAccount(anyString(), any());
  }

  @Test
  @Transactional
  void createAccount_existingAccount_returnsExistingAndDoesNotCallStripeAgain() throws Exception {
    // given — first call creates the account
    seedTestData();
    String stripeAccountId = stubStripeSuccess();

    CreateAccountRequest request =
        CreateAccountRequest.builder()
            .ownerId(testOwner.getId())
            .domainId(fiatDomain.getId())
            .currency("USD")
            .build();

    AccountResponse firstResponse = accountService.createAccount(request);

    // when — second call with same owner + domain + currency
    AccountResponse secondResponse = accountService.createAccount(request);

    // then — same account returned (idempotent)
    assertThat(secondResponse.getId()).isEqualTo(firstResponse.getId());
    assertThat(secondResponse.getStripeAccountId()).isEqualTo(stripeAccountId);

    // then — Stripe called only once (first provisioning), not on second call
    verify(stripeAccountService).createAccount(anyString(), any());
  }

  @Test
  @Transactional
  void createAccount_cryptoDomain_throwsUnsupportedDomain() throws Exception {
    // given
    seedTestData();
    CreateAccountRequest request =
        CreateAccountRequest.builder()
            .ownerId(testOwner.getId())
            .domainId(cryptoDomain.getId())
            .currency("BTC")
            .build();

    // when / then
    assertThatThrownBy(() -> accountService.createAccount(request))
        .isInstanceOf(ApplicationException.class);

    // then — no account created, no Stripe call
    assertThat(accountRepository.findByOwnerIdAndStatus(testOwner.getId(), AccountStatus.ACTIVE))
        .isEmpty();
    verify(stripeAccountService, never()).createAccount(anyString(), any());
  }

  @Test
  void createAccount_stripeFailure_throwsAndRollsBackAccountCreation() throws Exception {
    // given
    seedTestData();
    when(stripeAccountService.createAccount(anyString(), any()))
        .thenThrow(mock(StripeException.class));

    CreateAccountRequest request =
        CreateAccountRequest.builder()
            .ownerId(testOwner.getId())
            .domainId(fiatDomain.getId())
            .currency("EUR")
            .build();

    // when / then
    assertThatThrownBy(() -> accountService.createAccount(request))
        .isInstanceOf(ApplicationException.class);

    // then — no account persisted due to transaction rollback
    String expectedCoa =
        InternalCoaFactory.build(testOwner.getId(), "FIAT", "EUR", BucketEnum.AVAILABLE);
    assertThat(accountRepository.findByInternalCoa(expectedCoa)).isEmpty();
  }

  @Test
  @Transactional
  void createAccount_currencyNormalization_uppercasesInput() throws Exception {
    // given
    seedTestData();
    stubStripeSuccess();

    CreateAccountRequest request =
        CreateAccountRequest.builder()
            .ownerId(testOwner.getId())
            .domainId(fiatDomain.getId())
            .currency("eur")
            .build();

    // when
    AccountResponse response = accountService.createAccount(request);

    // then — currency stored as uppercase
    assertThat(response.getCurrency()).isEqualTo("EUR");

    // then — internal CoA path uses uppercase currency
    String expectedCoa =
        InternalCoaFactory.build(testOwner.getId(), "FIAT", "EUR", BucketEnum.AVAILABLE);
    assertThat(accountRepository.findByInternalCoa(expectedCoa)).isPresent();
  }
}
