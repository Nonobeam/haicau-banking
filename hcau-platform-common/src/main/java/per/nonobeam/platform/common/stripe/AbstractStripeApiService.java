package per.nonobeam.platform.common.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import java.util.function.Supplier;

public abstract class AbstractStripeApiService {

  private final Supplier<String> secretKeySupplier;
  private volatile StripeClient stripeClient;

  protected AbstractStripeApiService(Supplier<String> secretKeySupplier) {
    this.secretKeySupplier = secretKeySupplier;
  }

  protected StripeClient client() {
    if (stripeClient == null) {
      synchronized (this) {
        if (stripeClient == null) {
          stripeClient = new StripeClient(secretKeySupplier.get());
        }
      }
    }
    return stripeClient;
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
