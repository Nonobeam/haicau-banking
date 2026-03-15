package per.nonobeam.web.model.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

  @NotNull(message = "Owner ID must not be null")
  private UUID ownerId;

  @NotBlank(message = "Domain ID must not be blank")
  private UUID domainId;

  @NotBlank(message = "Currency must not be blank")
  private String currency;
}
