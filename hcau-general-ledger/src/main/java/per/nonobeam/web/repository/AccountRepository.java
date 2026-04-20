package per.nonobeam.web.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

  Optional<Account> findByCoaPath(String coaPath);

  List<Account> findByOwnerIdAndStatus(String ownerId, AccountStatus status);

  List<Account> findByWalletId(String walletId);
}
