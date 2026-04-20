package per.nonobeam.web.model.deposit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Tracks each DEPOSIT_CONFIRMED credit entry for bank settlement reconciliation (Decision #44). */
@Entity
@Table(name = "deposit_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositMetadata {

  @Id private String ledgerEntryId;

  private String transactionId;

  private String accountId;

  private BigDecimal amount;

  private String currency;

  @Builder.Default private Boolean isSettled = false;

  private OffsetDateTime settledAt;

  @CreationTimestamp private OffsetDateTime createdAt;
}
