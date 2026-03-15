package per.nonobeam.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.id.UlidGeneratedId;

@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

  @Id
  @UlidGeneratedId(prefix = "entr")
  private String id;

  private Long seq;

  @ManyToOne
  @JoinColumn(name = "transaction_id")
  private Transaction transaction;

  @ManyToOne
  @JoinColumn(name = "account_id")
  private Account account;

  private Long amount;

  @Enumerated(EnumType.STRING)
  private EntryType entryType;

  @CreationTimestamp private OffsetDateTime createdAt;
}
