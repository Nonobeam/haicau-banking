package per.nonobeam.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.id.HcauId;
import per.nonobeam.common.id.HcauIdGenerator;

@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

  @Id
  @HcauId(prefix = "entr")
  private String id;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = HcauIdGenerator.generate("entr");
    }
  }

  private Long seq;

  @ManyToOne
  @JoinColumn(name = "transaction_id")
  private Transaction transaction;

  @ManyToOne
  @JoinColumn(name = "account_id")
  private Account account;

  /** CREDIT or DEBIT. Stored as column 'type'. */
  @Enumerated(EnumType.STRING)
  @jakarta.persistence.Column(name = "type")
  private EntryType type;

  /** Positive amount. Currency is tracked separately per entry. */
  private BigDecimal amount;

  /** ISO 4217 currency code (e.g. USD). */
  private String currency;

  @CreationTimestamp private OffsetDateTime createdAt;
}
