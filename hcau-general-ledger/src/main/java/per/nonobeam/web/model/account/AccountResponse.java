package per.nonobeam.web.model.account;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import per.nonobeam.common.account.AccountState;
import per.nonobeam.common.account.CoaPath;
import per.nonobeam.common.account.CoaPathParser;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

  private String id;
  private String ownerId;
  private String coaPath;
  private String accountType;
  private String state;
  private String walletId;
  private AccountStatus status;
  private OffsetDateTime createdAt;

  public static AccountResponse mapToResponse(Account account) {
    String coaPath = account.getCoaPath();
    String accountType = null;
    String state = null;

    if (coaPath != null) {
      try {
        CoaPath parsed = CoaPathParser.parse(coaPath);
        accountType = parsed.accountType().name();
        AccountState parsedState = parsed.state();
        state = parsedState != null ? parsedState.name() : null;
      } catch (IllegalArgumentException ignored) {
        // unparseable legacy path — leave accountType/state null
      }
    }

    return AccountResponse.builder()
        .id(account.getId())
        .ownerId(account.getOwner() != null ? account.getOwner().getId() : null)
        .coaPath(coaPath)
        .accountType(accountType)
        .state(state)
        .walletId(account.getWalletId())
        .status(account.getStatus())
        .createdAt(account.getCreatedAt())
        .build();
  }
}
