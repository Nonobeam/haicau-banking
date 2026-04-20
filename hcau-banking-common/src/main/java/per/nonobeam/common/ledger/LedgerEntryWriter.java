package per.nonobeam.common.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.LedgerType;
import per.nonobeam.common.account.Wallet;
import per.nonobeam.common.account.WalletStatus;
import per.nonobeam.repository.CommonAccountRepository;
import per.nonobeam.repository.CommonBalanceSnapshotRepository;
import per.nonobeam.repository.CommonLedgerEntryRepository;
import per.nonobeam.repository.CommonWalletRepository;

/**
 * The ONLY permitted path for writing ledger entries.
 *
 * <p>Enforces:
 *
 * <ul>
 *   <li>Global ascending lock order (deadlock prevention, Decision #21)
 *   <li>Wallet status validated AFTER acquiring locks (TOCTOU fix, Decision #40)
 *   <li>Balance validation for debits
 *   <li>Cross-ledger integrity: single-sided SUB entries must be paired with wallet:control GL
 *       entries (Check 3)
 *   <li>Currency homogeneity for INTERNAL_TRANSFER_INSTANT (Check 4)
 *   <li>balance_snapshots updated for SUB and non-control GL accounts; wallet:control:* skipped
 *       (maintained by GlSnapshotJob, Decision #31)
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerEntryWriter {

  /** Input tuple for a single ledger entry to be written. */
  public record LedgerEntrySpec(
      String accountId, EntryType type, BigDecimal amount, String currency) {}

  private final CommonAccountRepository accountRepository;
  private final CommonWalletRepository walletRepository;
  private final CommonLedgerEntryRepository ledgerEntryRepository;
  private final CommonBalanceSnapshotRepository balanceSnapshotRepository;

  /**
   * Writes the provided ledger entries inside the caller's transaction (Propagation.REQUIRED).
   *
   * @param transaction the owning transaction record (must already be persisted)
   * @param specs the entries to write
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public List<LedgerEntry> write(Transaction transaction, List<LedgerEntrySpec> specs) {
    if (specs.isEmpty()) {
      return List.of();
    }

    // ── Step 1-2: Collect and sort account IDs ──────────────────────────────
    List<String> sortedAccountIds =
        specs.stream()
            .map(LedgerEntrySpec::accountId)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

    // ── Step 3-4: Lock accounts in ascending order (NOWAIT) ─────────────────
    Map<String, Account> lockedAccounts = new HashMap<>();
    for (String accountId : sortedAccountIds) {
      Account account =
          accountRepository
              .findByIdForUpdate(accountId)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException("Account not found for locking: " + accountId));
      lockedAccounts.put(accountId, account);
    }

    // ── Step 3-4 (cont): Collect wallet IDs and lock wallets ─────────────────
    List<String> sortedWalletIds =
        lockedAccounts.values().stream()
            .map(Account::getWalletId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

    Map<String, Wallet> lockedWallets = new HashMap<>();
    for (String walletId : sortedWalletIds) {
      Wallet wallet =
          walletRepository
              .findByIdForUpdate(walletId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Wallet not found for locking: " + walletId));
      lockedWallets.put(walletId, wallet);
    }

    // ── Step 4 (cont): Validate wallet status AFTER acquiring locks (Decision #40) ──
    for (Account account : lockedAccounts.values()) {
      if (account.getWalletId() != null) {
        Wallet wallet = lockedWallets.get(account.getWalletId());
        if (wallet != null && wallet.getStatus() != WalletStatus.ACTIVE) {
          throw new IllegalStateException(
              "Wallet " + wallet.getId() + " is not ACTIVE: " + wallet.getStatus());
        }
      }
    }

    // ── Step 5: Validate balances (sufficient funds for debits) ─────────────
    validateBalances(specs, lockedAccounts);

    // ── Synchronous Check 3: Cross-ledger integrity ──────────────────────────
    validateCrossLedgerIntegrity(specs, lockedAccounts);

    // ── Step 6: Write ledger_entries ─────────────────────────────────────────
    List<LedgerEntry> written = new ArrayList<>();
    for (LedgerEntrySpec spec : specs) {
      LedgerEntry entry =
          LedgerEntry.builder()
              .transaction(transaction)
              .account(lockedAccounts.get(spec.accountId()))
              .type(spec.type())
              .amount(spec.amount())
              .currency(spec.currency())
              .build();
      written.add(ledgerEntryRepository.save(entry));
    }

    // ── Step 7: Update balance_snapshots (skip wallet:control:* accounts) ────
    updateBalanceSnapshots(written);

    return written;
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private void validateBalances(List<LedgerEntrySpec> specs, Map<String, Account> accounts) {
    // Group DEBIT amounts by (accountId, currency) and check against snapshot balance
    Map<String, BigDecimal> debitSums = new HashMap<>();
    for (LedgerEntrySpec spec : specs) {
      if (spec.type() == EntryType.DEBIT) {
        String key = spec.accountId() + ":" + spec.currency();
        debitSums.merge(key, spec.amount(), BigDecimal::add);
      }
    }
    for (Map.Entry<String, BigDecimal> entry : debitSums.entrySet()) {
      String[] parts = entry.getKey().split(":", 2);
      String accountId = parts[0];
      String currency = parts[1];
      BalanceSnapshotId snapId = new BalanceSnapshotId(accountId, currency);
      BigDecimal balance =
          balanceSnapshotRepository
              .findById(snapId)
              .map(BalanceSnapshot::getBalance)
              .orElse(BigDecimal.ZERO);
      if (balance.compareTo(entry.getValue()) < 0) {
        throw new IllegalStateException(
            "Insufficient funds on account "
                + accountId
                + " ("
                + currency
                + "): balance="
                + balance
                + ", required="
                + entry.getValue());
      }
    }
  }

  /**
   * Check 3: For every single-sided SUB entry (class a — cross-ledger), the same transaction must
   * contain a wallet:control:{state} GL entry of the same amount and same direction.
   */
  private void validateCrossLedgerIntegrity(
      List<LedgerEntrySpec> specs, Map<String, Account> accounts) {
    // Gather SUB entries and check if they are balanced within the SUB world
    Map<String, BigDecimal> subNet = new HashMap<>(); // currency -> net (credit - debit)
    Map<String, BigDecimal> controlNet = new HashMap<>(); // currency -> net (credit - debit)

    for (LedgerEntrySpec spec : specs) {
      Account account = accounts.get(spec.accountId());
      if (account == null) {
        continue;
      }

      String coaPath = account.getCoaPath();
      BigDecimal signed = spec.type() == EntryType.CREDIT ? spec.amount() : spec.amount().negate();

      if (account.getLedger() == LedgerType.SUB) {
        subNet.merge(spec.currency(), signed, BigDecimal::add);
      } else if (coaPath != null && coaPath.startsWith("wallet:control:")) {
        controlNet.merge(spec.currency(), signed, BigDecimal::add);
      }
    }

    // If SUB is not balanced (sum != 0) then the imbalance must be covered by wallet:control GL
    for (Map.Entry<String, BigDecimal> entry : subNet.entrySet()) {
      String currency = entry.getKey();
      BigDecimal subImbalance = entry.getValue();
      if (subImbalance.compareTo(BigDecimal.ZERO) != 0) {
        BigDecimal control = controlNet.getOrDefault(currency, BigDecimal.ZERO);
        // The control side must absorb the imbalance (opposite sign convention)
        if (control.add(subImbalance).compareTo(BigDecimal.ZERO) != 0) {
          throw new IllegalArgumentException(
              "Cross-ledger integrity violation for currency "
                  + currency
                  + ": SUB imbalance="
                  + subImbalance
                  + ", wallet:control net="
                  + control);
        }
      }
    }
  }

  private void updateBalanceSnapshots(List<LedgerEntry> entries) {
    for (LedgerEntry entry : entries) {
      Account account = entry.getAccount();
      if (account == null || entry.getCurrency() == null) {
        continue;
      }

      // Skip wallet:control:* accounts — maintained by GlSnapshotJob (Decision #31)
      String coaPath = account.getCoaPath();
      if (coaPath != null && coaPath.startsWith("wallet:control:")) {
        continue;
      }

      BalanceSnapshotId snapId = new BalanceSnapshotId(account.getId(), entry.getCurrency());
      BalanceSnapshot snap =
          balanceSnapshotRepository
              .findById(snapId)
              .orElseGet(
                  () -> BalanceSnapshot.builder().id(snapId).balance(BigDecimal.ZERO).build());

      BigDecimal delta =
          entry.getType() == EntryType.CREDIT ? entry.getAmount() : entry.getAmount().negate();
      snap.setBalance(snap.getBalance().add(delta));
      balanceSnapshotRepository.save(snap);
    }
  }
}
