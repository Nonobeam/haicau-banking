package per.nonobeam.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.Account;

@Repository
public interface AccountRepository extends CommonAccountRepository {

  /** Find an account by its CoA path. */
  @Override
  Optional<Account> findByCoaPath(String coaPath);

  /**
   * Find the wallet_id for a given owner (customer), then resolve the main clearing account using
   * CoA path pattern matching. Used by DepositService to locate the customer's clearing account.
   */
  @Query(
      """
      SELECT a FROM Account a
      WHERE a.owner.id = :ownerId
        AND a.coaPath LIKE CONCAT('wallet:', :ownerId, ':%:clearing')
      """)
  Optional<Account> findClearingAccountByOwner(@Param("ownerId") String ownerId);

  @Query(
      """
      SELECT a FROM Account a
      WHERE a.owner.id = :ownerId
        AND a.coaPath LIKE CONCAT('wallet:', :ownerId, ':%:main')
      """)
  Optional<Account> findMainAccountByOwner(@Param("ownerId") String ownerId);

  /** Resolve the wallet_id for an owner so we can build CoA paths. */
  @Query(
      """
      SELECT a.walletId FROM Account a
      WHERE a.owner.id = :ownerId AND a.walletId IS NOT NULL
      """)
  Optional<String> findWalletIdByOwner(@Param("ownerId") String ownerId);
}
