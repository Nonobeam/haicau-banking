package per.nonobeam.common.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobTrackingId implements Serializable {

  private String jobName;

  private String transactionId;
}
