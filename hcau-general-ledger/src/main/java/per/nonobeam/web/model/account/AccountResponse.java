package per.nonobeam.web.model.account;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.InternalCoa;
import per.nonobeam.web.common.account.InternalCoaFactory;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
  private UUID id;
  private UUID ownerId;
  private String internalCoa;
  private String domain;
  private String currency;
  private String bucketType;
  private AccountStatus status;
  private OffsetDateTime createdAt;

  public static AccountResponse mapToResponse(Account account) {
    InternalCoa parsed = InternalCoaFactory.parse(account.getInternalCoa());
    return AccountResponse.builder()
        .id(account.getId())
        .ownerId(account.getOwner().getId())
        .internalCoa(account.getInternalCoa())
        .domain(parsed.domainName())
        .currency(parsed.currency())
        .bucketType(parsed.bucket().name())
        .status(account.getStatus())
        .createdAt(account.getCreatedAt())
        .build();
  }
}
