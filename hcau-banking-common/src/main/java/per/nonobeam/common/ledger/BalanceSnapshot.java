package per.nonobeam.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

  @Id private String accountId;

  private Long balance;

  private Long lastProcessedEntrySeq;

  @UpdateTimestamp private OffsetDateTime updatedAt;
}
