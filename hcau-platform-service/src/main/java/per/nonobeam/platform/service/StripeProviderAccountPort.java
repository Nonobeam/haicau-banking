package per.nonobeam.platform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import per.nonobeam.common.provider.AbstractProviderAccountPort;
import per.nonobeam.common.provider.ProvisionedAccount;

@Component
@RequiredArgsConstructor
public class StripeProviderAccountPort extends AbstractProviderAccountPort {

  private final StripeProviderAccountService stripeService;

  @Override
  public ProvisionedAccount createProviderAccount(String userId) {
    return stripeService.create(userId);
  }
}
