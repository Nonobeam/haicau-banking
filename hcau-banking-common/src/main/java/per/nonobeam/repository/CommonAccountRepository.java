package per.nonobeam.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.Account;

@Repository
public interface CommonAccountRepository extends JpaRepository<Account, String> {

  Optional<Account> findByCoaPath(String coaPath);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(
      @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query("SELECT a FROM Account a WHERE a.id = :id")
  Optional<Account> findByIdForUpdate(@Param("id") String id);
}
