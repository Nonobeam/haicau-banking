package per.nonobeam.web.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.web.common.account.Account;

@Entity
@Table(name = "ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private Long seq;

  @ManyToOne
  @JoinColumn(name = "transaction_id")
  private Transaction transaction;

  @ManyToOne
  @JoinColumn(name = "account_id")
  private Account account;

  private BigDecimal credit;

  private BigDecimal debit;

  @CreationTimestamp private OffsetDateTime createdAt;
}
