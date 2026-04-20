package per.nonobeam.service;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.AccountState;
import per.nonobeam.common.account.CoaPathParser;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntry;
import per.nonobeam.common.ledger.LedgerEntryWriter;
import per.nonobeam.common.ledger.LedgerEntryWriter.LedgerEntrySpec;
import per.nonobeam.common.ledger.Transaction;
import per.nonobeam.common.ledger.TransactionStatus;
import per.nonobeam.common.ledger.TransactionType;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.repository.AccountRepository;
import per.nonobeam.repository.DepositMetadataRepository;
import per.nonobeam.repository.DepositRepository;
import per.nonobeam.repository.LedgerEntryRepository;
import per.nonobeam.repository.TransactionTypeRepository;
import per.nonobeam.web.common.provider.WebhookPayload;
import per.nonobeam.web.model.deposit.DepositMetadata;

/**
 * Handles Stripe webhooks for the deposit lifecycle (Stage 2-3 and failure reversal).
 *
 * <p>Stage 3 (DEPOSIT_CONFIRMED) — two sub-transactions:
 *
 * <ol>
 *   <li>Sub-tx 1 (GL only): close receivable, open external float at Stripe
 *   <li>Sub-tx 2 (GL + SUB): clearing → main for customer wallet
 * </ol>
 *
 * <p>Failure/reversal (DEPOSIT_REVERSAL): release clearing funds back to receivable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositWebhookService {

  private static final String PROVIDER_NAME = "stripe";

  private final DepositRepository depositRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountRepository accountRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final LedgerEntryWriter ledgerEntryWriter;
  private final DepositMetadataRepository depositMetadataRepository;

  @Transactional
  public void handle(WebhookPayload payload) {
    Transaction transaction =
        depositRepository.findByMetadataSessionId(payload.sessionId()).orElse(null);
    if (transaction == null) {
      log.warn("No transaction found for sessionId={}", payload.sessionId());
      return;
    }

    switch (transaction.getStatus()) {
      case COMPLETED, FAILED, VOIDED, EXPIRED -> {
        log.info(
            "Transaction {} already in terminal state {}, ignoring webhook",
            transaction.getId(),
            transaction.getStatus());
        return;
      }
      default -> {
        /* proceed */
      }
    }

    switch (payload.outcome()) {
      case COMPLETED -> handleCompleted(transaction, payload);
      case FAILED -> handleReversal(transaction, TransactionStatus.FAILED, payload);
      case VOIDED -> handleReversal(transaction, TransactionStatus.VOIDED, payload);
      case EXPIRED -> handleReversal(transaction, TransactionStatus.EXPIRED, payload);
      default ->
          log.warn(
              "Unhandled outcome {} for transaction {}", payload.outcome(), transaction.getId());
    }
  }

  // ── Stage 3: DEPOSIT_CONFIRMED ─────────────────────────────────────────────

  private void handleCompleted(Transaction originalTx, WebhookPayload payload) {
    // Resolve accounts from the original DEPOSIT_RECEIVABLE entries
    List<LedgerEntry> originalEntries =
        ledgerEntryRepository.findByTransactionId(originalTx.getId());

    // Find the customer's clearing account from the original SUB entry
    Account customerClearing =
        originalEntries.stream()
            .filter(
                e -> e.getType() == EntryType.CREDIT && isCustomerClearingAccount(e.getAccount()))
            .map(LedgerEntry::getAccount)
            .findFirst()
            .orElse(null);

    if (customerClearing == null) {
      log.error("No customer clearing entry found for transaction {}", originalTx.getId());
      return;
    }

    BigDecimal amount =
        originalEntries.stream()
            .filter(
                e -> e.getType() == EntryType.CREDIT && isCustomerClearingAccount(e.getAccount()))
            .map(LedgerEntry::getAmount)
            .findFirst()
            .orElse(BigDecimal.ZERO);

    String currency =
        originalEntries.stream()
            .filter(
                e -> e.getType() == EntryType.CREDIT && isCustomerClearingAccount(e.getAccount()))
            .map(LedgerEntry::getCurrency)
            .findFirst()
            .orElse("USD");

    // Resolve GL accounts
    Account receivable =
        requireAccount(
            CoaPathParser.receivablePath(PROVIDER_NAME), "receivable:counterparty:stripe");
    Account external =
        requireAccount(CoaPathParser.externalPath(PROVIDER_NAME), "external:counterparty:stripe");
    Account walletControlClearing =
        requireAccount(
            CoaPathParser.walletControlPath(AccountState.CLEARING), "wallet:control:clearing");
    Account walletControlMain =
        requireAccount(CoaPathParser.walletControlPath(AccountState.MAIN), "wallet:control:main");

    // Resolve customer main account (same walletId, different state)
    String walletId = customerClearing.getWalletId();
    String ownerId = customerClearing.getOwner().getId();
    Account customerMain =
        accountRepository
            .findMainAccountByOwner(ownerId)
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.ACCOUNT_NOT_FOUND, ownerId + ":main"));

    // Sub-tx 1 (GL only): close receivable, record external float
    TransactionType depositConfirmedType = requireTxType("DEPOSIT_CONFIRMED");
    Transaction subTx1 =
        depositRepository.save(
            Transaction.builder()
                .idempotencyKey(payload.referenceId() + "_DEPOSIT_CONFIRMED_1")
                .transactionType(depositConfirmedType)
                .status(TransactionStatus.COMPLETED)
                .traceId(originalTx.getTraceId())
                .causationId(payload.referenceId())
                .sourceService("hcau-banking-reconcile")
                .build());

    ledgerEntryWriter.write(
        subTx1,
        List.of(
            new LedgerEntrySpec(external.getId(), EntryType.DEBIT, amount, currency),
            new LedgerEntrySpec(receivable.getId(), EntryType.CREDIT, amount, currency)));

    // Sub-tx 2 (GL + SUB): clearing → main
    Transaction subTx2 =
        depositRepository.save(
            Transaction.builder()
                .idempotencyKey(payload.referenceId() + "_DEPOSIT_CONFIRMED_2")
                .transactionType(depositConfirmedType)
                .status(TransactionStatus.COMPLETED)
                .traceId(originalTx.getTraceId())
                .causationId(subTx1.getId())
                .sourceService("hcau-banking-reconcile")
                .build());

    List<LedgerEntry> subTx2Entries =
        ledgerEntryWriter.write(
            subTx2,
            List.of(
                // GL: wallet:control:clearing DEBIT, wallet:control:main CREDIT
                new LedgerEntrySpec(
                    walletControlClearing.getId(), EntryType.DEBIT, amount, currency),
                new LedgerEntrySpec(walletControlMain.getId(), EntryType.CREDIT, amount, currency),
                // SUB: customer clearing DEBIT, customer main CREDIT (intra-wallet class b)
                new LedgerEntrySpec(customerClearing.getId(), EntryType.DEBIT, amount, currency),
                new LedgerEntrySpec(customerMain.getId(), EntryType.CREDIT, amount, currency)));

    // Persist deposit_metadata for bank settlement reconciliation (Decision #44)
    LedgerEntry mainCreditEntry =
        subTx2Entries.stream()
            .filter(
                e ->
                    e.getAccount().getId().equals(customerMain.getId())
                        && e.getType() == EntryType.CREDIT)
            .findFirst()
            .orElse(null);

    if (mainCreditEntry != null) {
      depositMetadataRepository.save(
          DepositMetadata.builder()
              .ledgerEntryId(mainCreditEntry.getId())
              .transactionId(subTx2.getId())
              .accountId(customerMain.getId())
              .amount(amount)
              .currency(currency)
              .isSettled(false)
              .build());
    }

    // Mark original transaction as COMPLETED
    originalTx.setReferenceId(payload.referenceId());
    originalTx.setStatus(TransactionStatus.COMPLETED);
    depositRepository.save(originalTx);
  }

  // ── Failure / Reversal (DEPOSIT_REVERSAL) ─────────────────────────────────

  private void handleReversal(
      Transaction transaction, TransactionStatus status, WebhookPayload payload) {
    List<LedgerEntry> originalEntries =
        ledgerEntryRepository.findByTransactionId(transaction.getId());

    Account customerClearing =
        originalEntries.stream()
            .filter(
                e -> e.getType() == EntryType.CREDIT && isCustomerClearingAccount(e.getAccount()))
            .map(LedgerEntry::getAccount)
            .findFirst()
            .orElse(null);

    if (customerClearing == null) {
      log.error(
          "No customer clearing entry found for reversal of transaction {}", transaction.getId());
      transaction.setStatus(status);
      depositRepository.save(transaction);
      return;
    }

    BigDecimal amount =
        originalEntries.stream()
            .filter(
                e -> e.getType() == EntryType.CREDIT && isCustomerClearingAccount(e.getAccount()))
            .map(LedgerEntry::getAmount)
            .findFirst()
            .orElse(BigDecimal.ZERO);

    String currency =
        originalEntries.stream()
            .filter(
                e -> e.getType() == EntryType.CREDIT && isCustomerClearingAccount(e.getAccount()))
            .map(LedgerEntry::getCurrency)
            .findFirst()
            .orElse("USD");

    Account receivable =
        requireAccount(
            CoaPathParser.receivablePath(PROVIDER_NAME), "receivable:counterparty:stripe");
    Account walletControlClearing =
        requireAccount(
            CoaPathParser.walletControlPath(AccountState.CLEARING), "wallet:control:clearing");

    TransactionType reversalType = requireTxType("DEPOSIT_REVERSAL");

    Transaction reversalTx =
        depositRepository.save(
            Transaction.builder()
                .idempotencyKey(transaction.getId() + "_REVERSAL")
                .transactionType(reversalType)
                .status(TransactionStatus.COMPLETED)
                .traceId(transaction.getTraceId())
                .causationId(transaction.getId())
                .sourceService("hcau-banking-reconcile")
                .build());

    // GL DEBIT wallet:control:clearing, GL CREDIT receivable; SUB DEBIT customer clearing
    ledgerEntryWriter.write(
        reversalTx,
        List.of(
            new LedgerEntrySpec(walletControlClearing.getId(), EntryType.DEBIT, amount, currency),
            new LedgerEntrySpec(receivable.getId(), EntryType.CREDIT, amount, currency),
            new LedgerEntrySpec(customerClearing.getId(), EntryType.DEBIT, amount, currency)));

    transaction.setReferenceId(payload.referenceId());
    transaction.setStatus(status);
    depositRepository.save(transaction);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private boolean isCustomerClearingAccount(Account account) {
    if (account == null || account.getCoaPath() == null) {
      return false;
    }
    // customer clearing paths look like wallet:{customerId}:{walletId}:clearing
    // NOT wallet:control:clearing
    return account.getCoaPath().endsWith(":clearing")
        && !account.getCoaPath().startsWith("wallet:control:");
  }

  private Account requireAccount(String coaPath, String label) {
    return accountRepository
        .findByCoaPath(coaPath)
        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, label));
  }

  private TransactionType requireTxType(String name) {
    return transactionTypeRepository
        .findByName(name)
        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INVALID_REQUEST, name));
  }
}
