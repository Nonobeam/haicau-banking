package per.nonobeam.common.account;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.common.id.UlidGeneratedId;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id
  @UlidGeneratedId(prefix = "user")
  private String id;

  private String name;

  @ManyToOne
  @JoinColumn(name = "user_type")
  private UserType userType;

  @CreationTimestamp private OffsetDateTime createdAt;
}
