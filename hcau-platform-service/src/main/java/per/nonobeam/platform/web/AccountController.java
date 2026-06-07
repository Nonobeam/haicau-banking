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
import per.nonobeam.platform.config.setting.GeneralLedger;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final GeneralLedger generalLedger;
  private final HttpCommunicationChannel channel;

  @PostMapping
  public AccountResponse createAccount(@Valid @RequestBody LedgerAccountRequest request) {
    return channel
        .push(
            generalLedger.getEndpoints().getCreateAccount(),
            ChannelRequest.of(request),
            AccountResponse.class)
        .body();
  }

  @PostMapping("/currencies")
  public void enableCurrency(@Valid @RequestBody EnableCurrencyRequest request) {
    channel.push(
        generalLedger.getEndpoints().getCreateCurrencyAccount(),
        ChannelRequest.of(request),
        Void.class);
  }
}
