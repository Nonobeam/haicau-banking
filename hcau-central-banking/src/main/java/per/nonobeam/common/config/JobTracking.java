package per.nonobeam.common.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "job_tracking")
@IdClass(JobTrackingId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobTracking {

  @Id private String jobName;

  @Id private String transactionId;

  private Boolean completed;

  private String skippedReason;

  private OffsetDateTime processedAt;
}
