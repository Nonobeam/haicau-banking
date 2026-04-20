package per.nonobeam.web.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.common.id.HcauId;
import per.nonobeam.common.id.HcauIdGenerator;

@Entity
@Table(name = "transaction_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionType {

  @Id
  @HcauId(prefix = "ttyp")
  private String id;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = HcauIdGenerator.generate("ttyp");
    }
  }

  private String name;

  private String description;

  /** True if this transaction type requires balanced GL entries. */
  private Boolean glRequired;

  /** Wallet state(s) required for SUB entries, or null if no SUB entries needed. */
  private String subRequired;

  @CreationTimestamp private OffsetDateTime createdAt;
}
