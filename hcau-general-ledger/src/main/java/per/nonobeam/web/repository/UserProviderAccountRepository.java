package per.nonobeam.web.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.UserProviderAccount;

@Repository
public interface UserProviderAccountRepository extends JpaRepository<UserProviderAccount, String> {

  Optional<UserProviderAccount> findByUserIdAndProviderCode(String userId, String providerCode);
}
