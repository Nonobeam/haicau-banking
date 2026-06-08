package per.nonobeam.platform.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_contacts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContact {

  @Id
  @Column(name = "user_id")
  private String userId;

  private String email;

  @Column(name = "telegram_chat_id")
  private String telegramChatId;

  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
