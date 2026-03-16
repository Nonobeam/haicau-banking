package per.nonobeam.common.config;

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
@Table(name = "idempotency_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

  @Id private String idempotencyKey;

  private Integer responseStatus;

  private String responseBody;

  @CreationTimestamp private OffsetDateTime createdAt;

  private OffsetDateTime expiresAt;
}
