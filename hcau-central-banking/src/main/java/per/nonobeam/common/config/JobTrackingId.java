package per.nonobeam.common.config;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobTrackingId implements Serializable {

  private String jobName;

  private String transactionId;
}
