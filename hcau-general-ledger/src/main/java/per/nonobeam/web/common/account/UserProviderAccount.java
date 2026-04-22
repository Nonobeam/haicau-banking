package per.nonobeam.web.common.account;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import per.nonobeam.common.id.HcauIdGenerator;

@Entity
@Table(name = "user_provider_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProviderAccount {

  @Id private String id;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = HcauIdGenerator.generate("upa");
    }
  }

  @ManyToOne
  @JoinColumn(name = "user_id")
  private User user;

  private String providerCode;

  private String externalAccountId;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private UserProviderAccountStatus status = UserProviderAccountStatus.ACTIVE;

  @CreationTimestamp private OffsetDateTime createdAt;
}
