package per.nonobeam.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.Wallet;

@Repository
public interface CommonWalletRepository extends JpaRepository<Wallet, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(
      @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query("SELECT w FROM Wallet w WHERE w.id = :id")
  Optional<Wallet> findByIdForUpdate(@Param("id") String id);

  Optional<Wallet> findByCustomerId(String customerId);
}
