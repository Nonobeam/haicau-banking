package per.nonobeam.common.account;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserType {

  @Id private String id;

  private String name;

  private String description;
}
