package per.nonobeam.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import per.nonobeam.common.id.HcauId;
import per.nonobeam.common.id.HcauIdGenerator;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

  @Id
  @HcauId(prefix = "trnx")
  private String id;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = HcauIdGenerator.generate("trnx");
    }
  }

  private String idempotencyKey;

  @ManyToOne
  @JoinColumn(name = "transaction_type")
  private TransactionType transactionType;

  private String referenceId;

  private String actorId;

  /** Trace ID: links all transactions belonging to the same business flow (e.g. a deposit). */
  @jakarta.persistence.Column(name = "trace_id")
  private String traceId;

  /**
   * Causation ID: the immediate cause of this transaction (e.g. the DEPOSIT_RECEIVABLE tx that
   * triggered DEPOSIT_CONFIRMED sub-tx 2). May also hold an external provider reference.
   */
  private String causationId;

  private String sourceService;

  private String providerId;

  @JdbcTypeCode(SqlTypes.JSON)
  private String metadata;

  @CreationTimestamp private OffsetDateTime createdAt;

  @Enumerated(EnumType.STRING)
  private TransactionStatus status;
}
