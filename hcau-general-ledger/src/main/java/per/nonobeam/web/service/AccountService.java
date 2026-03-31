package per.nonobeam.web.service;

import static per.nonobeam.web.common.account.BucketEnum.AVAILABLE;
import static per.nonobeam.web.common.account.BucketEnum.RESERVED;
import static per.nonobeam.web.common.account.DomainEnum.CRYPTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.param.v2.core.AccountCreateParams;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.BucketEnum;
import per.nonobeam.web.common.account.DomainType;
import per.nonobeam.web.common.account.InternalCoaFactory;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.model.account.AccountResponse;
import per.nonobeam.web.model.account.CreateAccountRequest;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.service.stripe.StripeAccountService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;
  private final CommonQueryService commonQueryService;
  private final StripeAccountService stripeAccountService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public Account getAccount(UUID id) {
    return commonQueryService.getAccount(id, AccountStatus.ACTIVE);
  }

  @Transactional
  public AccountResponse createAccount(CreateAccountRequest request) {
    DomainType domainType = commonQueryService.getDomainTypeById(request.getDomainId());

    if (domainType.isDomain(CRYPTO)) {
      throw new ApplicationException(
          ApplicationErrorCode.UNSUPPORTED_DOMAIN, request.getDomainId());
    }

    User owner = commonQueryService.getUser(request.getOwnerId());
    String requestedCurrency = request.getCurrency().toUpperCase(Locale.ROOT);

    Map<BucketEnum, String> internalCoas = buildInternalCoas(owner, domainType, requestedCurrency);

    Account primaryAccount =
        accountRepository
            .findByInternalCoa(internalCoas.get(AVAILABLE))
            .orElseGet(
                () ->
                    provisionPrimaryAccount(
                        owner, domainType, requestedCurrency, internalCoas.get(AVAILABLE)));

    ensureReservedAccountExists(owner, internalCoas.get(RESERVED));

    return AccountResponse.mapToResponse(primaryAccount);
  }

  private Map<BucketEnum, String> buildInternalCoas(
      User owner, DomainType domainType, String currency) {
    Map<BucketEnum, String> coaMap = new EnumMap<>(BucketEnum.class);
    coaMap.put(
        AVAILABLE, InternalCoaFactory.build(owner.getId(), domainType.getName(), currency, AVAILABLE));
    coaMap.put(
        RESERVED, InternalCoaFactory.build(owner.getId(), domainType.getName(), currency, RESERVED));
    return coaMap;
  }

  private Account provisionPrimaryAccount(
      User owner, DomainType domainType, String currency, String internalCoa) {
    Map<String, String> metadata = buildStripeMetadata(owner, domainType, currency, internalCoa);
    Account stripeAccount = createStripeAccount(internalCoa, metadata);

    Account newAccount =
        Account.builder()
            .owner(owner)
            .internalCoa(internalCoa)
            .status(AccountStatus.ACTIVE)
            .stripeAccountId(stripeAccount.getId())
            .stripeMetadata(serializeMetadata(metadata))
            .build();
    Account savedAccount = accountRepository.save(newAccount);
    log.info(
        "Account created for bucket {} with Stripe account {}",
        AVAILABLE,
        stripeAccount.getId());
    return savedAccount;
  }

  private void ensureReservedAccountExists(User owner, String reservedInternalCoa) {
    createAccountIfNotExists(owner, RESERVED, reservedInternalCoa);
  }

  private Account createAccountIfNotExists(
      User owner, BucketEnum bucket, String internalCoa) {
    return accountRepository
        .findByInternalCoa(internalCoa)
        .orElseGet(
            () -> {
              Account newAccount =
                  Account.builder()
                      .owner(owner)
                      .internalCoa(internalCoa)
                      .status(AccountStatus.ACTIVE)
                      .build();
              accountRepository.save(newAccount);
              log.info("Account created for bucket {}", bucket);
              return newAccount;
            });
  }

  private Account createStripeAccount(String idempotencyKey, Map<String, String> metadata) {
    AccountCreateParams.Builder builder = AccountCreateParams.builder();
    metadata.forEach(builder::putMetadata);
    try {
      return stripeAccountService.createAccount(idempotencyKey, builder.build());
    } catch (StripeException e) {
      log.error("Failed to create Stripe account for {}", idempotencyKey, e);
      throw new ApplicationException(ApplicationErrorCode.INTERNAL_CALLING_ERROR, e.getMessage());
    }
  }

  private Map<String, String> buildStripeMetadata(
      User owner, DomainType domainType, String currency, String internalCoa) {
    return Map.of(
        "internal_coa", internalCoa,
        "owner_id", owner.getId().toString(),
        "domain", domainType.getName(),
        "currency", currency,
        "bucket", AVAILABLE.name());
  }

  private String serializeMetadata(Map<String, String> metadata) {
    try {
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize Stripe metadata {}", metadata, e);
      throw new ApplicationException(ApplicationErrorCode.INTERNAL_SERVER_ERROR, "stripe_metadata");
    }
  }
}
