package per.nonobeam.web.common.ledger;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
import per.nonobeam.web.common.account.Account;

@Entity
@Table(name = "balance_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSnapshot {

  @EmbeddedId private BalanceSnapshotId id;

  @ManyToOne
  @JoinColumn(name = "account_id")
  private Account account;

  private BigDecimal availableBalance;

  private BigDecimal reservedBalance;

  @ManyToOne
  @JoinColumn(name = "last_available_entry_id")
  private LedgerEntry lastAvailableEntry;

  @ManyToOne
  @JoinColumn(name = "last_reserved_entry_id")
  private LedgerEntry lastReservedEntry;

  @UpdateTimestamp private OffsetDateTime updatedAt;
}
