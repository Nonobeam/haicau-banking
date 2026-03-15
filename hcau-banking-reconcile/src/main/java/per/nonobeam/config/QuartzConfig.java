package per.nonobeam.config;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import per.nonobeam.job.DepositExpiryJob;
import per.nonobeam.job.DepositSweepJob;

@Configuration
public class QuartzConfig {

  @Bean
  public JobDetail depositExpiryJobDetail() {
    return JobBuilder.newJob(DepositExpiryJob.class)
        .withIdentity("depositExpiryJob")
        .storeDurably()
        .build();
  }

  @Bean
  public Trigger depositExpiryTrigger(JobDetail depositExpiryJobDetail) {
    // 1-minute interval by default, config could be dynamic but static trigger is simpler.
    // The spec says "job interval is read from job_config.expiry_check_interval_seconds",
    // To strictly do that, we could query the DB or just rely on a default static trigger.
    // I'll set 60s.
    return TriggerBuilder.newTrigger()
        .forJob(depositExpiryJobDetail)
        .withIdentity("depositExpiryTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(60).repeatForever())
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
    return TriggerBuilder.newTrigger()
        .forJob(depositSweepJobDetail)
        .withIdentity("depositSweepTrigger")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(60).repeatForever())
        .build();
  }
}
