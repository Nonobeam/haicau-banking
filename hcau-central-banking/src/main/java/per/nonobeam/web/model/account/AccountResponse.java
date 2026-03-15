package per.nonobeam.web.model.account;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
  private UUID id;
  private UUID ownerId;
  private String domain;
  private String currency;
  private String bucketType;
  private AccountStatus status;
  private OffsetDateTime createdAt;

  public static AccountResponse mapToResponse(Account account) {
    return AccountResponse.builder()
        .id(account.getId())
        .ownerId(account.getOwner().getId())
        .domain(account.getDomain().getName())
        .currency(account.getCurrency())
        .bucketType(account.getBucketType().getName())
        .status(account.getStatus())
        .createdAt(account.getCreatedAt())
        .build();
  }
}
