package per.nonobeam.web.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByInternalCoa(String internalCoa);

  Optional<Account> findByOwnerIdAndStatus(UUID ownerId, AccountStatus status);
}
