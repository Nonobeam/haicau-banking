package per.nonobeam.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.domain.GlSnapshotState;
import per.nonobeam.domain.GlSnapshotStateId;

@Repository
public interface GlSnapshotStateRepository
    extends JpaRepository<GlSnapshotState, GlSnapshotStateId> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT s FROM GlSnapshotState s
      WHERE s.id.accountId = :accountId AND s.id.currency = :currency
      """)
  Optional<GlSnapshotState> findForUpdate(
      @Param("accountId") String accountId, @Param("currency") String currency);
}
