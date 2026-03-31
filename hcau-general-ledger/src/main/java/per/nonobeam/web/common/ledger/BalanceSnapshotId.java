package per.nonobeam.web.common.ledger;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSnapshotId implements Serializable {

  private UUID ownerId;

  private UUID domain;

  private String currency;
}
