package per.nonobeam.job;

import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.config.JobConfig;
import per.nonobeam.repository.GlProcessedEntryRepository;
import per.nonobeam.repository.JobConfigRepository;

/**
 * Purges old rows from {@code gl_processed_entries} (Decision #45).
 *
 * <p>The safety window must be larger than the overlap window used by {@link GlSnapshotJob} (1
 * minute) so entries are never purged before they've been processed. Default safety window is 5
 * minutes.
 *
 * <p>Runs separately from GlSnapshotJob to avoid extending its lock duration.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class GlProcessedEntriesPurgeJob extends QuartzJobBean {

  private final GlProcessedEntryRepository processedEntryRepository;
  private final JobConfigRepository jobConfigRepository;

  @Override
  @Transactional
  protected void executeInternal(@NonNull JobExecutionContext context) {
    boolean enabled =
        Boolean.parseBoolean(
            jobConfigRepository
                .findById("gl_purge_enabled")
                .map(JobConfig::getValue)
                .orElse("true"));
    if (!enabled) {
      return;
    }

    long safetyWindowSec =
        Long.parseLong(
            jobConfigRepository
                .findById("gl_purge_safety_window_sec")
                .map(JobConfig::getValue)
                .orElse("300"));

    int batchSize =
        Integer.parseInt(
            jobConfigRepository
                .findById("gl_purge_batch_size")
                .map(JobConfig::getValue)
                .orElse("10000"));

    OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofSeconds(safetyWindowSec));

    int totalDeleted = 0;
    int deleted;
    do {
      deleted = processedEntryRepository.deleteOlderThan(cutoff, batchSize);
      totalDeleted += deleted;
    } while (deleted > 0);

    if (totalDeleted > 0) {
      log.info("GlProcessedEntriesPurgeJob: purged {} rows", totalDeleted);
    }
  }
}
