package per.nonobeam.web.common.ledger;

import jakarta.persistence.Column;
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

  @JdbcTypeCode(SqlTypes.JSON)
  private String metadata;

  @CreationTimestamp private OffsetDateTime createdAt;

  @Enumerated(EnumType.STRING)
  private TransactionStatus status;

  @Column(name = "trace_id")
  private String traceId;

  private String causationId;

  private String actorId;

  private String sourceService;

  private String providerId;
}
