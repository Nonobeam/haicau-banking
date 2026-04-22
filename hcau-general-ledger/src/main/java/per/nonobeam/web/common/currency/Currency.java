package per.nonobeam.web.common.currency;

import jakarta.persistence.Column;
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
@Table(name = "currencies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Currency {

  @Id private String code;

  private String name;

  @Column(name = "decimal_places")
  private short decimalPlaces;

  @Column(name = "display_decimals")
  private short displayDecimals;

  @Column(name = "is_active")
  @Builder.Default
  private boolean isActive = true;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
}
