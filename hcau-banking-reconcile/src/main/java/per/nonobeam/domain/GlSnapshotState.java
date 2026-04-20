package per.nonobeam.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tracks the last-processed timestamp per (control account, currency) for GlSnapshotJob. */
@Entity
@Table(name = "gl_snapshot_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlSnapshotState {

  @EmbeddedId private GlSnapshotStateId id;

  private OffsetDateTime lastRunAt;
}
