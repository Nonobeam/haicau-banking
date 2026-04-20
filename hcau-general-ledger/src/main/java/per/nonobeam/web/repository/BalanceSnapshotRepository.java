package per.nonobeam.web.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.ledger.BalanceSnapshot;
import per.nonobeam.web.common.ledger.BalanceSnapshotId;

@Repository
public interface BalanceSnapshotRepository
    extends JpaRepository<BalanceSnapshot, BalanceSnapshotId> {}
