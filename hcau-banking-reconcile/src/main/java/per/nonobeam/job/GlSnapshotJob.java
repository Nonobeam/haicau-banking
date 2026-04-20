package per.nonobeam.job;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import per.nonobeam.common.config.JobConfig;
import per.nonobeam.common.ledger.BalanceSnapshot;
import per.nonobeam.common.ledger.BalanceSnapshotId;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntry;
import per.nonobeam.domain.GlProcessedEntry;
import per.nonobeam.domain.GlSnapshotState;
import per.nonobeam.domain.GlSnapshotStateId;
import per.nonobeam.repository.CommonAccountRepository;
import per.nonobeam.repository.CommonBalanceSnapshotRepository;
import per.nonobeam.repository.CommonLedgerEntryRepository;
import per.nonobeam.repository.GlProcessedEntryRepository;
import per.nonobeam.repository.GlSnapshotStateRepository;
import per.nonobeam.repository.JobConfigRepository;

/**
 * Maintains {@code balance_snapshots} for {@code wallet:control:*} GL aggregate accounts.
 *
 * <p>Uses an overlap-window approach (Decision #42): polls by {@code created_at} with a backward
 * overlap of 1 minute, uses {@code gl_processed_entries} for deduplication. Locks the {@code
 * gl_snapshot_state} row per (account, currency) to prevent concurrent runs.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class GlSnapshotJob extends QuartzJobBean {

  private static final Duration OVERLAP_WINDOW = Duration.ofMinutes(1);
  private static final String CURRENCY = "USD";

  private final CommonAccountRepository accountRepository;
  private final CommonLedgerEntryRepository ledgerEntryRepository;
  private final CommonBalanceSnapshotRepository balanceSnapshotRepository;
  private final GlSnapshotStateRepository snapshotStateRepository;
  private final GlProcessedEntryRepository processedEntryRepository;
  private final JobConfigRepository jobConfigRepository;

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    boolean enabled =
        Boolean.parseBoolean(
            jobConfigRepository
                .findById("gl_snapshot_enabled")
                .map(JobConfig::getValue)
                .orElse("true"));

    if (!enabled) {
      log.debug("GlSnapshotJob disabled via job_config");
      return;
    }

    log.info("GlSnapshotJob starting");

    List<Account> controlAccounts =
        accountRepository.findAll().stream()
            .filter(a -> a.getCoaPath() != null && a.getCoaPath().startsWith("wallet:control:"))
            .toList();

    for (Account account : controlAccounts) {
      try {
        processControlAccount(account.getId(), CURRENCY);
      } catch (Exception e) {
        log.error("GlSnapshotJob failed for account {}: {}", account.getId(), e.getMessage(), e);
      }
    }

    log.info("GlSnapshotJob complete");
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processControlAccount(String accountId, String currency) {
    // Step 1: Lock state row to prevent concurrent runs
    GlSnapshotStateId stateId = new GlSnapshotStateId(accountId, currency);
    GlSnapshotState state =
        snapshotStateRepository
            .findForUpdate(accountId, currency)
            .orElseGet(
                () -> {
                  GlSnapshotState fresh =
                      GlSnapshotState.builder()
                          .id(stateId)
                          .lastRunAt(OffsetDateTime.now().minus(OVERLAP_WINDOW))
                          .build();
                  return snapshotStateRepository.save(fresh);
                });

    OffsetDateTime windowStart = state.getLastRunAt().minus(OVERLAP_WINDOW);

    // Step 2: Fetch all ledger entries in the overlap window, excluding already-processed
    List<LedgerEntry> allEntries =
        ledgerEntryRepository.findAll().stream()
            .filter(le -> le.getAccount().getId().equals(accountId))
            .filter(le -> currency.equals(le.getCurrency()))
            .filter(le -> le.getCreatedAt() != null && !le.getCreatedAt().isBefore(windowStart))
            .toList();

    List<String> alreadyProcessedIds =
        processedEntryRepository.findAll().stream()
            .map(GlProcessedEntry::getLedgerEntryId)
            .toList();

    List<LedgerEntry> newEntries =
        allEntries.stream().filter(le -> !alreadyProcessedIds.contains(le.getId())).toList();

    if (newEntries.isEmpty()) {
      log.debug("GlSnapshotJob: no new entries for account {} currency {}", accountId, currency);
    } else {
      // Step 3: Compute delta
      BigDecimal delta = BigDecimal.ZERO;
      for (LedgerEntry entry : newEntries) {
        if (entry.getType() == EntryType.CREDIT) {
          delta = delta.add(entry.getAmount());
        } else {
          delta = delta.subtract(entry.getAmount());
        }
      }

      // Step 4: Apply delta to balance_snapshot
      BalanceSnapshotId snapId = new BalanceSnapshotId(accountId, currency);
      BalanceSnapshot snap =
          balanceSnapshotRepository
              .findById(snapId)
              .orElseGet(
                  () -> BalanceSnapshot.builder().id(snapId).balance(BigDecimal.ZERO).build());
      snap.setBalance(snap.getBalance().add(delta));
      balanceSnapshotRepository.save(snap);

      // Step 5: Record processed entry IDs
      List<GlProcessedEntry> toSave = new ArrayList<>();
      for (LedgerEntry entry : newEntries) {
        toSave.add(GlProcessedEntry.builder().ledgerEntryId(entry.getId()).build());
      }
      processedEntryRepository.saveAll(toSave);

      log.info(
          "GlSnapshotJob: account={} currency={} delta={} newEntries={}",
          accountId,
          currency,
          delta,
          newEntries.size());
    }

    // Step 6: Advance last_run_at
    state.setLastRunAt(OffsetDateTime.now());
    snapshotStateRepository.save(state);
  }
}
