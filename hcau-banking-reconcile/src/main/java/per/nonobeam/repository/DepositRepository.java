package per.nonobeam.repository;

import java.util.List;
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
}
