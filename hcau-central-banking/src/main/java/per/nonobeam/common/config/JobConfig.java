package per.nonobeam.common.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "job_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobConfig {

  @Id private String key;

  private String value;

  private String description;

  @UpdateTimestamp private OffsetDateTime updatedAt;
}
