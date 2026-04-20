package per.nonobeam.config;

import lombok.RequiredArgsConstructor;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import per.nonobeam.common.config.JobConfig;
import per.nonobeam.job.DepositExpiryJob;
import per.nonobeam.job.DepositSweepJob;
import per.nonobeam.job.GlProcessedEntriesPurgeJob;
import per.nonobeam.job.GlSnapshotJob;
import per.nonobeam.job.ProvisioningReconcileJob;
import per.nonobeam.repository.JobConfigRepository;

@Configuration
@RequiredArgsConstructor
public class QuartzConfig {

  private final JobConfigRepository jobConfigRepository;

  @Bean
  public JobDetail depositExpiryJobDetail() {
    return JobBuilder.newJob(DepositExpiryJob.class)
        .withIdentity("depositExpiryJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger depositExpiryTrigger(JobDetail depositExpiryJobDetail) {
    int intervalSeconds =
        Integer.parseInt(
            jobConfigRepository
                .findById("expiry_check_interval_seconds")
                .map(JobConfig::getValue)
                .orElse("60"));

    return TriggerBuilder.newTrigger()
        .forJob(depositExpiryJobDetail)
        .withIdentity("depositExpiryTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever())
        .build();
  }

  @Bean
  public JobDetail depositSweepJobDetail() {
    return JobBuilder.newJob(DepositSweepJob.class)
        .withIdentity("depositSweepJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger depositSweepTrigger(JobDetail depositSweepJobDetail) {
    int intervalSeconds =
        Integer.parseInt(
            jobConfigRepository
                .findById("sweep_interval_seconds")
                .map(JobConfig::getValue)
                .orElse("120"));

    return TriggerBuilder.newTrigger()
        .forJob(depositSweepJobDetail)
        .withIdentity("depositSweepTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever())
        .build();
  }

  @Bean
  public JobDetail glSnapshotJobDetail() {
    return JobBuilder.newJob(GlSnapshotJob.class)
        .withIdentity("glSnapshotJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger glSnapshotTrigger(JobDetail glSnapshotJobDetail) {
    int intervalSeconds =
        Integer.parseInt(
            jobConfigRepository
                .findById("gl_snapshot_run_interval_sec")
                .map(JobConfig::getValue)
                .orElse("30"));

    return TriggerBuilder.newTrigger()
        .forJob(glSnapshotJobDetail)
        .withIdentity("glSnapshotTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever())
        .build();
  }

  @Bean
  public JobDetail glProcessedEntriesPurgeJobDetail() {
    return JobBuilder.newJob(GlProcessedEntriesPurgeJob.class)
        .withIdentity("glProcessedEntriesPurgeJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger glProcessedEntriesPurgeTrigger(JobDetail glProcessedEntriesPurgeJobDetail) {
    int intervalSeconds =
        Integer.parseInt(
            jobConfigRepository
                .findById("gl_purge_run_interval_sec")
                .map(JobConfig::getValue)
                .orElse("60"));

    return TriggerBuilder.newTrigger()
        .forJob(glProcessedEntriesPurgeJobDetail)
        .withIdentity("glProcessedEntriesPurgeTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever())
        .build();
  }

  @Bean
  public JobDetail provisioningReconcileJobDetail() {
    return JobBuilder.newJob(ProvisioningReconcileJob.class)
        .withIdentity("provisioningReconcileJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger provisioningReconcileTrigger(JobDetail provisioningReconcileJobDetail) {
    int intervalSeconds =
        Integer.parseInt(
            jobConfigRepository
                .findById("provisioning_reconcile_run_interval_sec")
                .map(JobConfig::getValue)
                .orElse("60"));

    return TriggerBuilder.newTrigger()
        .forJob(provisioningReconcileJobDetail)
        .withIdentity("provisioningReconcileTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(intervalSeconds)
                .repeatForever())
        .build();
  }
}
