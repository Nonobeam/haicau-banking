package per.nonobeam.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.Account;

@Repository
public interface AccountRepository extends CommonAccountRepository {

  @Query(
      """
      SELECT a
      FROM Account a
      WHERE a.owner.id = :ownerId
        AND a.currency = :currency
        AND a.bucketType.name = :bucketName
      """)
  Optional<Account> findAccountByOwnerAndCurrencyAndBucketName(
      @Param("ownerId") String ownerId,
      @Param("currency") String currency,
      @Param("bucketName") String bucketName);
}
