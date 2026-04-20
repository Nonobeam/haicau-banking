package per.nonobeam.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.ledger.Transaction;

@Repository
public interface DepositRepository extends CommonDepositRepository {
  @Query(
      value =
          "SELECT t.* FROM transactions t"
              + " WHERE t.status = 'PENDING'"
              + " AND t.transaction_type = (SELECT id FROM transaction_types"
              + " WHERE name = 'INBOUND_DEPOSIT')"
              + " AND ((t.metadata->>'sessionId' IS NOT NULL"
              + " AND t.created_at < NOW() - CAST(:sessionTimeout || ' minutes' AS INTERVAL))"
              + " OR (t.metadata->>'sessionId' IS NULL"
              + " AND t.created_at < NOW()"
              + " - CAST(:noSessionTimeout || ' minutes' AS INTERVAL)))"
              + " AND t.id NOT IN (SELECT transaction_id FROM job_tracking"
              + " WHERE job_name = 'DEPOSIT_EXPIRY' AND completed = TRUE)",
      nativeQuery = true)
  List<Transaction> findExpiredDeposits(
      @Param("sessionTimeout") long sessionTimeout,
      @Param("noSessionTimeout") long noSessionTimeout);

  @Query(
      value =
          "SELECT t.* FROM transactions t"
              + " WHERE t.status = 'COMPLETED'"
              + " AND t.transaction_type = (SELECT id FROM transaction_types"
              + " WHERE name = 'INBOUND_DEPOSIT')"
              + " AND t.id NOT IN (SELECT transaction_id FROM job_tracking"
              + " WHERE job_name = 'DEPOSIT_SWEEP')",
      nativeQuery = true)
  List<Transaction> findPendingSweeps();

  @Query(
      value =
          "SELECT * FROM transactions t"
              + " WHERE t.transaction_type ="
              + " (SELECT id FROM transaction_types WHERE name = 'INBOUND_DEPOSIT')"
              + " AND t.status = 'PENDING'"
              + " AND t.metadata::jsonb ->> 'sessionId' = :sessionId"
              + " LIMIT 1",
      nativeQuery = true)
  Optional<Transaction> findByMetadataSessionId(@Param("sessionId") String sessionId);
}
