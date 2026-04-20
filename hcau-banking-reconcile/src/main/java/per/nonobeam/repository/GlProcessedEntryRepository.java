package per.nonobeam.repository;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import per.nonobeam.domain.GlProcessedEntry;

@Repository
public interface GlProcessedEntryRepository extends JpaRepository<GlProcessedEntry, String> {

  @Modifying
  @Query(
      value =
          """
          DELETE FROM gl_processed_entries
          WHERE ledger_entry_id IN (
              SELECT gpe.ledger_entry_id
              FROM gl_processed_entries gpe
              JOIN ledger_entries le ON gpe.ledger_entry_id = le.id
              WHERE le.created_at < :cutoff
              LIMIT :batchSize
          )
          """,
      nativeQuery = true)
  int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff, @Param("batchSize") int batchSize);
}
