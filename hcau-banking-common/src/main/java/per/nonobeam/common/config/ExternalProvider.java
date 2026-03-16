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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "external_providers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalProvider {

  @Id private String id;

  private String name;

  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  private String config;

  @CreationTimestamp private OffsetDateTime createdAt;
}
