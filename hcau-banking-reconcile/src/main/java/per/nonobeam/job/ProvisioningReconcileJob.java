package per.nonobeam.job;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.AccountState;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.CoaPathParser;
import per.nonobeam.common.account.LedgerType;
import per.nonobeam.common.account.User;
import per.nonobeam.common.account.Wallet;
import per.nonobeam.common.account.WalletProvisioningStatus;
import per.nonobeam.common.account.WalletStatus;
import per.nonobeam.common.config.JobConfig;
import per.nonobeam.common.ledger.BalanceSnapshot;
import per.nonobeam.common.ledger.BalanceSnapshotId;
import per.nonobeam.repository.CommonAccountRepository;
import per.nonobeam.repository.CommonBalanceSnapshotRepository;
import per.nonobeam.repository.CommonUserRepository;
import per.nonobeam.repository.CommonWalletRepository;
import per.nonobeam.repository.JobConfigRepository;
import per.nonobeam.repository.WalletProvisioningStatusRepository;

/**
 * Detects users (type=USER) whose wallet provisioning failed or never ran, and provisions the
 * missing resources (Decision #47).
 *
 * <p>Idempotent: each step checks before inserting. Partial provisioning from a previous crash is
 * completed. Commits per user so one failure does not block others.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class ProvisioningReconcileJob extends QuartzJobBean {

  private final CommonUserRepository userRepository;
  private final CommonWalletRepository walletRepository;
  private final CommonAccountRepository accountRepository;
  private final CommonBalanceSnapshotRepository balanceSnapshotRepository;
  private final WalletProvisioningStatusRepository walletProvisioningStatusRepository;
  private final JobConfigRepository jobConfigRepository;

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    boolean enabled =
        Boolean.parseBoolean(
            jobConfigRepository
                .findById("provisioning_reconcile_enabled")
                .map(JobConfig::getValue)
                .orElse("true"));
    if (!enabled) {
      return;
    }

    int batchSize =
        Integer.parseInt(
            jobConfigRepository
                .findById("provisioning_reconcile_batch_size")
                .map(JobConfig::getValue)
                .orElse("100"));

    String defaultCurrency =
        jobConfigRepository
            .findById("provisioning_reconcile_default_currency")
            .map(JobConfig::getValue)
            .orElse("USD");

    // Find unprovisioned USER-type users
    List<User> unprovisionedUsers = findUnprovisionedUsers(batchSize);
    log.info("ProvisioningReconcileJob: found {} unprovisioned users", unprovisionedUsers.size());

    for (User user : unprovisionedUsers) {
      try {
        provisionUser(user, defaultCurrency);
      } catch (Exception e) {
        log.error(
            "ProvisioningReconcileJob: failed to provision user {}: {}",
            user.getId(),
            e.getMessage(),
            e);
      }
    }
  }

  private List<User> findUnprovisionedUsers(int limit) {
    // All USER-type users who don't have a wallet_provisioning_status entry
    List<String> provisionedIds =
        walletProvisioningStatusRepository.findAll().stream()
            .map(WalletProvisioningStatus::getUserId)
            .toList();

    return userRepository.findAll(PageRequest.of(0, limit * 10)).stream()
        .filter(u -> u.getUserType() != null && "USER".equalsIgnoreCase(u.getUserType().getName()))
        .filter(u -> !provisionedIds.contains(u.getId()))
        .limit(limit)
        .toList();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void provisionUser(User user, String currency) {
    String userId = user.getId();

    // Step a: check/create wallets row
    Wallet wallet =
        walletRepository
            .findByCustomerId(userId)
            .orElseGet(
                () ->
                    walletRepository.save(
                        Wallet.builder()
                            .customer(user)
                            .isPrimary(true)
                            .status(WalletStatus.ACTIVE)
                            .build()));

    // Step b: create 3 SUB accounts if missing
    for (AccountState state :
        List.of(AccountState.MAIN, AccountState.RESERVED, AccountState.CLEARING)) {
      String coaPath = CoaPathParser.walletPath(userId, wallet.getId(), state);
      accountRepository
          .findByCoaPath(coaPath)
          .orElseGet(
              () -> {
                Account account =
                    accountRepository.save(
                        Account.builder()
                            .owner(user)
                            .coaPath(coaPath)
                            .ledger(LedgerType.SUB)
                            .walletId(wallet.getId())
                            .status(AccountStatus.ACTIVE)
                            .build());
                // create balance_snapshot
                balanceSnapshotRepository.save(
                    BalanceSnapshot.builder()
                        .id(new BalanceSnapshotId(account.getId(), currency))
                        .balance(BigDecimal.ZERO)
                        .build());
                return account;
              });
    }

    // Step c: create per-customer GL accounts if missing
    for (String coaPath :
        List.of(CoaPathParser.offsetPath(userId), CoaPathParser.revenuePath(userId))) {
      accountRepository
          .findByCoaPath(coaPath)
          .orElseGet(
              () -> {
                Account account =
                    accountRepository.save(
                        Account.builder()
                            .owner(user)
                            .coaPath(coaPath)
                            .ledger(LedgerType.GL)
                            .status(AccountStatus.ACTIVE)
                            .build());
                balanceSnapshotRepository.save(
                    BalanceSnapshot.builder()
                        .id(new BalanceSnapshotId(account.getId(), currency))
                        .balance(BigDecimal.ZERO)
                        .build());
                return account;
              });
    }

    // Step d: record provisioning status
    if (!walletProvisioningStatusRepository.existsById(userId)) {
      walletProvisioningStatusRepository.save(
          WalletProvisioningStatus.builder().userId(userId).build());
    }

    log.info("ProvisioningReconcileJob: user {} provisioned successfully", userId);
  }
}
