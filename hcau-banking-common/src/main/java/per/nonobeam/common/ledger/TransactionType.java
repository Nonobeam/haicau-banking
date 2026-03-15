package per.nonobeam.common.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "transaction_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionType {

  @Id private String id;

  private String name;

  private String description;

  @CreationTimestamp private OffsetDateTime createdAt;
}
