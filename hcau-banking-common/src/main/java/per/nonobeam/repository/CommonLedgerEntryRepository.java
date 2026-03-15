package per.nonobeam.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.ledger.LedgerEntry;

@Repository
public interface CommonLedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
  List<LedgerEntry> findByTransactionId(String transactionId);
}
