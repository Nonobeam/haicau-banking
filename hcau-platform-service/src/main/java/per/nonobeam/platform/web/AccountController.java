package per.nonobeam.platform.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.common.account.AccountResponse;
import per.nonobeam.common.account.EnableCurrencyRequest;
import per.nonobeam.common.account.LedgerAccountRequest;
import per.nonobeam.common.channel.ChannelRequest;
import per.nonobeam.internal.channel.HttpCommunicationChannel;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

  private static final String PROVISION_DESTINATION = "general-ledger/internal/v1/accounts";
  private static final String ENABLE_CURRENCY_DESTINATION =
      "general-ledger/internal/v1/accounts/currencies";

  private final HttpCommunicationChannel channel;

  @PostMapping
  public AccountResponse createAccount(@Valid @RequestBody LedgerAccountRequest request) {
    return channel
        .push(PROVISION_DESTINATION, ChannelRequest.of(request), AccountResponse.class)
        .body();
  }

  @PostMapping("/currencies")
  public void enableCurrency(@Valid @RequestBody EnableCurrencyRequest request) {
    channel.push(ENABLE_CURRENCY_DESTINATION, ChannelRequest.of(request), Void.class);
  }
}
