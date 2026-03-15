package per.nonobeam.web.service;

import static per.nonobeam.web.common.account.BucketEnum.ACCOUNTED;
import static per.nonobeam.web.common.account.BucketEnum.RESERVED;
import static per.nonobeam.web.common.account.DomainEnum.CRYPTO;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.BucketType;
import per.nonobeam.web.common.account.DomainType;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.model.account.AccountResponse;
import per.nonobeam.web.model.account.CreateAccountRequest;
import per.nonobeam.web.repository.AccountRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;
  private final CommonQueryService commonQueryService;

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
    BucketType bucketAccount = commonQueryService.getBucketType(ACCOUNTED);
    BucketType bucketReserved = commonQueryService.getBucketType(RESERVED);

    String requestedCurrency = request.getCurrency().toUpperCase();

    Account mainAccount =
        createAndSaveAccountIfNotExists(owner, domainType, requestedCurrency, bucketAccount);
    createAndSaveAccountIfNotExists(owner, domainType, requestedCurrency, bucketReserved);

    return AccountResponse.mapToResponse(mainAccount);
  }

  private Account createAndSaveAccountIfNotExists(
      User owner, DomainType domain, String currency, BucketType bucketType) {
    return accountRepository
        .findByOwnerAndDomainAndCurrencyAndBucketType(owner, domain, currency, bucketType)
        .orElseGet(
            () -> {
              Account newAccount =
                  Account.builder()
                      .owner(owner)
                      .domain(domain)
                      .currency(currency)
                      .bucketType(bucketType)
                      .status(AccountStatus.ACTIVE)
                      .build();
              accountRepository.save(newAccount);
              log.info("Account created for bucket {}", bucketType);
              return newAccount;
            });
  }
}
