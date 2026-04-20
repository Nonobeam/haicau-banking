package per.nonobeam.common.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import per.nonobeam.common.id.HcauId;
import per.nonobeam.common.id.HcauIdGenerator;

@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

  @Id
  @HcauId(prefix = "acct")
  private String id;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = HcauIdGenerator.generate("acct");
    }
  }

  @ManyToOne
  @JoinColumn(name = "owner_id")
  private User owner;

  /** New CoA path (e.g. wallet:user_xxx:wllt_xxx:main). Populated by new provisioning. */
  @Column(name = "coa_path")
  private String coaPath;

  /** Ledger type: GL for system accounts, SUB for customer accounts. */
  @Enumerated(EnumType.STRING)
  @Column(name = "ledger")
  private LedgerType ledger;

  /** FK to wallets.id — null for GL-only accounts (bank, receivable, payable, external). */
  @Column(name = "wallet_id")
  private String walletId;

  @CreationTimestamp private OffsetDateTime createdAt;

  @UpdateTimestamp private OffsetDateTime updatedAt;

  @Enumerated(EnumType.STRING)
  private AccountStatus status;

  @Version private Long version;
}
