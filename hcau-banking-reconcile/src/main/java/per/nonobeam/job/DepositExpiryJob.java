package per.nonobeam.job;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.config.JobConfig;
import per.nonobeam.common.config.JobTracking;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntry;
import per.nonobeam.common.ledger.LedgerEntryWriter;
import per.nonobeam.common.ledger.LedgerEntryWriter.LedgerEntrySpec;
import per.nonobeam.common.ledger.Transaction;
import per.nonobeam.common.ledger.TransactionStatus;
import per.nonobeam.repository.DepositRepository;
import per.nonobeam.repository.JobConfigRepository;
import per.nonobeam.repository.JobTrackingRepository;
import per.nonobeam.repository.LedgerEntryRepository;

/**
 * Expires stale PENDING deposits by reversing their ledger entries (clearing → receivable).
 *
 * <p>Since deposits now go directly to DEPOSIT_REVERSAL on failure, this job handles the
 * session-timeout path where no webhook was received.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class DepositExpiryJob extends QuartzJobBean {

  private final DepositRepository depositRepository;
  private final JobConfigRepository jobConfigRepository;
  private final DepositExpiryProcessor expiryProcessor;

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context)
      throws JobExecutionException {
    log.info("Starting DepositExpiryJob");

    long sessionTimeout =
        Long.parseLong(
            jobConfigRepository
                .findById("session_timeout_minutes")
                .map(JobConfig::getValue)
                .orElse("30"));

    long noSessionTimeout =
        Long.parseLong(
            jobConfigRepository
                .findById("no_session_timeout_minutes")
                .map(JobConfig::getValue)
                .orElse("5"));

    List<Transaction> expiredTxns =
        depositRepository.findExpiredDeposits(sessionTimeout, noSessionTimeout);
    log.info("Found {} expired deposits to process", expiredTxns.size());

    for (Transaction tx : expiredTxns) {
      try {
        expiryProcessor.processExpiry(tx);
      } catch (Exception e) {
        log.error("Failed to process expiry for transaction {}", tx.getId(), e);
      }
    }
  }

  @Component
  @RequiredArgsConstructor
  public static class DepositExpiryProcessor {

    private final DepositRepository depositRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final JobTrackingRepository jobTrackingRepository;
    private final LedgerEntryWriter ledgerEntryWriter;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExpiry(Transaction tx) {
      tx.setStatus(TransactionStatus.EXPIRED);
      depositRepository.save(tx);

      List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());

      // Reverse each entry: CREDIT becomes DEBIT and vice versa.
      // LedgerEntryWriter handles balance updates and locking.
      List<LedgerEntrySpec> reversals =
          entries.stream()
              .map(
                  orig ->
                      new LedgerEntrySpec(
                          orig.getAccount().getId(),
                          orig.getType() == EntryType.CREDIT ? EntryType.DEBIT : EntryType.CREDIT,
                          orig.getAmount(),
                          orig.getCurrency() != null ? orig.getCurrency() : "USD"))
              .toList();

      if (!reversals.isEmpty()) {
        ledgerEntryWriter.write(tx, reversals);
      }

      JobTracking tracking =
          JobTracking.builder()
              .jobName("DEPOSIT_EXPIRY")
              .transactionId(tx.getId())
              .completed(true)
              .processedAt(OffsetDateTime.now())
              .build();
      jobTrackingRepository.save(tracking);
    }
  }
}
