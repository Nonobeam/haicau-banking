package per.nonobeam.web.service.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.param.v2.core.AccountCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import per.nonobeam.common.stripe.AbstractStripeApiService;
import per.nonobeam.common.stripe.StripeSetting;

@Service
@RequiredArgsConstructor
public class StripeAccountService extends AbstractStripeApiService {

  public StripeAccountService(StripeSetting stripeSetting) {
    super(stripeSetting);
  }

  public Account createAccount(String idempotencyKey, AccountCreateParams params)
      throws StripeException {
    return execute(
        idempotencyKey,
        requestOptions -> stripeClient.v2().core().accounts().create(params, requestOptions));
  }
}
