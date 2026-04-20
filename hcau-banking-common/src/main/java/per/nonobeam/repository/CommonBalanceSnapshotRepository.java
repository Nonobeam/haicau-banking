package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.ledger.BalanceSnapshot;
import per.nonobeam.common.ledger.BalanceSnapshotId;

@Repository
public interface CommonBalanceSnapshotRepository
    extends JpaRepository<BalanceSnapshot, BalanceSnapshotId> {}
