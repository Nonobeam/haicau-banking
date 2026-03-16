package per.nonobeam.web.model.deposit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {

  @NotBlank(message = "User ID must not be blank")
  private String userId;

  @NotBlank(message = "Currency must not be blank")
  private String currency;

  @NotNull(message = "Amount must not be null")
  private Long amount;
}
