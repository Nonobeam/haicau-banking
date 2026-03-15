package per.nonobeam.job;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.BucketEnum;
import per.nonobeam.common.config.JobTracking;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntry;
import per.nonobeam.common.ledger.Transaction;
import per.nonobeam.common.ledger.TransactionStatus;
import per.nonobeam.common.ledger.TransactionType;
import per.nonobeam.config.UlidGenerator;
import per.nonobeam.repository.AccountRepository;
import per.nonobeam.repository.DepositRepository;
import per.nonobeam.repository.JobTrackingRepository;
import per.nonobeam.repository.LedgerEntryRepository;
import per.nonobeam.repository.TransactionTypeRepository;

@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class DepositSweepJob extends QuartzJobBean {

  private final DepositRepository depositRepository;
  private final DepositSweepProcessor sweepProcessor;

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    log.info("Starting DepositSweepJob");
    List<Transaction> pendingSweeps = depositRepository.findPendingSweeps();
    log.info("Found {} pending sweeps to process", pendingSweeps.size());

    for (Transaction tx : pendingSweeps) {
      try {
        sweepProcessor.processSweep(tx);
      } catch (Exception e) {
        log.error("Failed to process sweep for transaction {}", tx.getId(), e);
      }
    }
  }

  @Component
  @RequiredArgsConstructor
  public static class DepositSweepProcessor {
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final JobTrackingRepository jobTrackingRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final DepositRepository depositRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSweep(Transaction tx) {
      List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
      if (entries.isEmpty()) {
        return;
      }

      Account reservedAccount =
          entries.stream()
              .filter(
                  e ->
                      e.getEntryType() == EntryType.CREDIT
                          && e.getAccount().getBucketType().isBucketType(BucketEnum.RESERVED))
              .findFirst()
              .map(LedgerEntry::getAccount)
              .orElse(null);

      if (reservedAccount == null) {
        return;
      }

      Account availableAccount =
          accountRepository
              .findAccountByOwnerAndCurrencyAndBucketName(
                  reservedAccount.getOwner().getId(), reservedAccount.getCurrency(), "AVAILABLE")
              .orElse(null);

      if (reservedAccount.getStatus() != AccountStatus.ACTIVE) {
        skipSweep(tx.getId(), "RESERVED account not ACTIVE");
        return;
      }
      if (availableAccount == null || availableAccount.getStatus() != AccountStatus.ACTIVE) {
        skipSweep(tx.getId(), "AVAILABLE account not ACTIVE or missing");
        return;
      }

      Long amount =
          entries.stream()
              .filter(e -> e.getEntryType() == EntryType.CREDIT)
              .findFirst()
              .orElseThrow()
              .getAmount();

      TransactionType sweepType =
          transactionTypeRepository
              .findByName("INBOUND_SWEEP")
              .orElseThrow(() -> new RuntimeException("Transaction pattern INBOUND_SWEEP missing"));

      String systemUserId = "usr_system";

      Transaction sweepTx =
          Transaction.builder()
              .id(UlidGenerator.generate("trnx"))
              .transactionType(sweepType)
              .status(TransactionStatus.COMPLETED)
              .actorId(systemUserId)
              .causationId(tx.getId())
              .correlationId(tx.getCorrelationId())
              .build();
      depositRepository.save(sweepTx);

      LedgerEntry debit =
          LedgerEntry.builder()
              .id(UlidGenerator.generate("entr"))
              .transaction(sweepTx)
              .account(reservedAccount)
              .amount(amount)
              .entryType(EntryType.DEBIT)
              .build();

      LedgerEntry credit =
          LedgerEntry.builder()
              .id(UlidGenerator.generate("entr"))
              .transaction(sweepTx)
              .account(availableAccount)
              .amount(amount)
              .entryType(EntryType.CREDIT)
              .build();

      ledgerEntryRepository.save(debit);
      ledgerEntryRepository.save(credit);

      JobTracking tracking =
          JobTracking.builder()
              .jobName("DEPOSIT_SWEEP")
              .transactionId(tx.getId())
              .completed(true)
              .processedAt(OffsetDateTime.now())
              .build();
      jobTrackingRepository.save(tracking);
    }

    private void skipSweep(String txId, String reason) {
      JobTracking tracking =
          JobTracking.builder()
              .jobName("DEPOSIT_SWEEP")
              .transactionId(txId)
              .completed(false)
              .skippedReason(reason)
              .processedAt(OffsetDateTime.now())
              .build();
      jobTrackingRepository.save(tracking);
    }
  }
}
