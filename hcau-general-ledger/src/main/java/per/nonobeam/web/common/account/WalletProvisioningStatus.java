package per.nonobeam.web.common.account;

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
@Table(name = "wallet_provisioning_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletProvisioningStatus {

  @Id private String userId;

  @CreationTimestamp private OffsetDateTime provisionedAt;
}
