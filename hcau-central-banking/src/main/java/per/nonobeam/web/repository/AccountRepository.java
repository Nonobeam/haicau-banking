package per.nonobeam.web.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.BucketType;
import per.nonobeam.web.common.account.DomainType;
import per.nonobeam.web.common.account.User;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByOwnerAndDomainAndCurrencyAndBucketType(
      User owner, DomainType domain, String currency, BucketType bucketType);

  Optional<Account> findByOwnerIdAndStatus(UUID ownerId, AccountStatus status);
}
