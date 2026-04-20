package per.nonobeam.common.ledger;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "balance_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSnapshot {

  @EmbeddedId private BalanceSnapshotId id;

  @Builder.Default private BigDecimal balance = BigDecimal.ZERO;

  @UpdateTimestamp private OffsetDateTime updatedAt;
}
