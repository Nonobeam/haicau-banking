package per.nonobeam.web.common.account;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "domain_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainType {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  private String description;

  @CreationTimestamp private OffsetDateTime createdAt;

  public boolean isDomain(DomainEnum domainEnum) {
    return this.name.equalsIgnoreCase(domainEnum.name());
  }
}
