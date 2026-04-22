package per.nonobeam.platform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.common.provider.CreateProviderAccountRequest;
import per.nonobeam.common.provider.ProvisionedAccount;
import per.nonobeam.platform.service.StripeProviderAccountService;

@RestController
@RequestMapping("/internal/v1/provider-accounts")
@RequiredArgsConstructor
public class ProviderAccountInternalController {

  private final StripeProviderAccountService stripeProviderAccountService;

  @PostMapping
  public ProvisionedAccount create(@RequestBody CreateProviderAccountRequest request) {
    return stripeProviderAccountService.create(request.userId());
  }
}
