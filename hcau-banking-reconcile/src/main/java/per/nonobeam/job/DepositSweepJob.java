package per.nonobeam.job;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * Retired: deposit clearing-to-main sweep is now handled synchronously by {@link
 * DepositWebhookService} when the provider webhook confirms the payment (DEPOSIT_CONFIRMED).
 *
 * <p>This class is kept as a no-op placeholder to avoid breaking Quartz scheduler configuration
 * that may reference this job class. Remove after scheduler config is updated.
 */
@Component
@Slf4j
public class DepositSweepJob extends QuartzJobBean {

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    log.info("DepositSweepJob is retired. Clearing to main is now handled synchronously.");
  }
}
