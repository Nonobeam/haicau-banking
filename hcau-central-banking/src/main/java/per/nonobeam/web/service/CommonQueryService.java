package per.nonobeam.web.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.BucketEnum;
import per.nonobeam.web.common.account.BucketType;
import per.nonobeam.web.common.account.DomainEnum;
import per.nonobeam.web.common.account.DomainType;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.BucketTypeRepository;
import per.nonobeam.web.repository.DomainTypeRepository;
import per.nonobeam.web.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CommonQueryService {

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final DomainTypeRepository domainTypeRepository;
  private final BucketTypeRepository bucketTypeRepository;

  public User getUser(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, id));
  }

  public DomainType getDomainType(DomainEnum domainEnum) {
    String name = domainEnum.name();
    return domainTypeRepository
        .findByName(name)
        .orElseGet(() -> domainTypeRepository.save(DomainType.builder().name(name).build()));
  }

  public BucketType getBucketType(BucketEnum bucketEnum) {
    String name = bucketEnum.name();
    return bucketTypeRepository
        .findByName(name)
        .orElseGet(() -> bucketTypeRepository.save(BucketType.builder().name(name).build()));
  }

  public Account getAccount(UUID id, AccountStatus status) {
    return accountRepository
        .findByOwnerIdAndStatus(id, status)
        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, id));
  }

  public DomainType getDomainTypeById(UUID id) {
    return domainTypeRepository
        .findById(id)
        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.DOMAIN_NOT_FOUND, id));
  }
}
