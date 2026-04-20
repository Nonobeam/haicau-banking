package per.nonobeam.web.common.ledger;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSnapshotId implements Serializable {

  private String accountId;

  private String currency;
}
