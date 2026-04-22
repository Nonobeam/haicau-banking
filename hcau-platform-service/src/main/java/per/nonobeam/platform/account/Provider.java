package per.nonobeam.platform.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "providers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Provider {

  @Id private String code;

  private String name;

  private String credentials;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ProviderStatus status = ProviderStatus.ACTIVE;

  @CreationTimestamp private OffsetDateTime createdAt;
  @UpdateTimestamp private OffsetDateTime updatedAt;
}
