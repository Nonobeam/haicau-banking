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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import per.nonobeam.common.id.UlidGeneratedId;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

  @Id
  @UlidGeneratedId(prefix = "trnx")
  private String id;

  private String idempotencyKey;

  @ManyToOne
  @JoinColumn(name = "transaction_type")
  private TransactionType transactionType;

  private String referenceId;

  private String actorId;

  private String correlationId;

  private String causationId;

  private String sourceService;

  private String providerId;

  @JdbcTypeCode(SqlTypes.JSON)
  private String metadata;

  @CreationTimestamp private OffsetDateTime createdAt;

  @Enumerated(EnumType.STRING)
  private TransactionStatus status;
}
