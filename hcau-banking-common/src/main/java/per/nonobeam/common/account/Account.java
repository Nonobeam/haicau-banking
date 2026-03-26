package per.nonobeam.common.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.common.id.UlidGeneratedId;

@Entity
@Table(name = "accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

  @Id
  @UlidGeneratedId(prefix = "acct")
  private String id;

  @ManyToOne
  @JoinColumn(name = "owner_id")
  private User owner;

  private String internalCoa;

  @CreationTimestamp private OffsetDateTime createdAt;

  @Enumerated(EnumType.STRING)
  private AccountStatus status;

  @Version private Long version;
}
