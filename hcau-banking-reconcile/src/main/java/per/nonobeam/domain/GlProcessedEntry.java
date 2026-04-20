package per.nonobeam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Dedup table for the overlap-window GlSnapshotJob (Decision #42). */
@Entity
@Table(name = "gl_processed_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlProcessedEntry {

  @Id private String ledgerEntryId;

  @CreationTimestamp private OffsetDateTime createdAt;
}
