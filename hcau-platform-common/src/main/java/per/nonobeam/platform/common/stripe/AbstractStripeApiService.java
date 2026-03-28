package per.nonobeam.common.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import jakarta.annotation.PostConstruct;

public abstract class AbstractStripeApiService {

  private final StripeSetting stripeSetting;

  protected StripeClient stripeClient;

  protected AbstractStripeApiService(StripeSetting stripeSetting) {
    this.stripeSetting = stripeSetting;
  }

  @PostConstruct
  void init() {
    this.stripeClient = new StripeClient(stripeSetting.getSecretKey());
  }

  protected RequestOptions idempotentOptions(String idempotencyKey) {
    return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
  }

  protected <T> T execute(String idempotencyKey, StripeCall<T> call) throws StripeException {
    return call.execute(idempotentOptions(idempotencyKey));
  }

  @FunctionalInterface
  public interface StripeCall<T> {
    T execute(RequestOptions options) throws StripeException;
  }
}
