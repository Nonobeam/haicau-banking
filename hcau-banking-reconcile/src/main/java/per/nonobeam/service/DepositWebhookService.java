package per.nonobeam.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.BucketEnum;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntry;
import per.nonobeam.common.ledger.Transaction;
import per.nonobeam.common.ledger.TransactionStatus;
import per.nonobeam.config.UlidGenerator;
import per.nonobeam.repository.AccountRepository;
import per.nonobeam.repository.DepositRepository;
import per.nonobeam.repository.LedgerEntryRepository;
import per.nonobeam.web.common.provider.WebhookPayload;

@Service
@RequiredArgsConstructor
public class DepositWebhookService {

  private final DepositRepository depositRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountRepository accountRepository;

  @Transactional
  public void handle(WebhookPayload payload) {
    Transaction transaction =
        depositRepository.findByMetadataSessionId(payload.sessionId()).orElse(null);
    if (transaction == null) {
      return;
    }

    switch (payload.outcome()) {
      case COMPLETED -> {
        transaction.setReferenceId(payload.referenceId());
        transaction.setStatus(TransactionStatus.COMPLETED);
        depositRepository.save(transaction);
      }
      case FAILED -> reverseAndSetStatus(transaction, TransactionStatus.FAILED, payload);
      case VOIDED -> reverseAndSetStatus(transaction, TransactionStatus.VOIDED, payload);
      case EXPIRED -> reverseAndSetStatus(transaction, TransactionStatus.EXPIRED, payload);
    }
  }

  private void reverseAndSetStatus(
      Transaction transaction, TransactionStatus status, WebhookPayload payload) {
    List<LedgerEntry> originalEntries =
        ledgerEntryRepository.findByTransactionId(transaction.getId());

    Account reservedAccount =
        originalEntries.stream()
            .filter(
                e ->
                    e.getEntryType() == EntryType.CREDIT
                        && e.getAccount().getBucketType().isBucketType(BucketEnum.RESERVED))
            .map(LedgerEntry::getAccount)
            .findFirst()
            .orElse(null);

    if (reservedAccount == null) {
      return;
    }

    Account bufferAccount =
        accountRepository
            .findAccountByOwnerAndCurrencyAndBucketName(
                "user_00000000000000000000000000SYSTEM",
                reservedAccount.getCurrency(),
                BucketEnum.ACCOUNTED.name())
            .orElse(null);

    if (bufferAccount == null) {
      return;
    }

    long amount =
        originalEntries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .findFirst()
            .map(LedgerEntry::getAmount)
            .orElse(0L);

    LedgerEntry reverseDebitReserved =
        LedgerEntry.builder()
            .id(UlidGenerator.generateEntryId())
            .transaction(transaction)
            .account(reservedAccount)
            .amount(amount)
            .entryType(EntryType.DEBIT)
            .build();

    LedgerEntry reverseCreditBuffer =
        LedgerEntry.builder()
            .id(UlidGenerator.generateEntryId())
            .transaction(transaction)
            .account(bufferAccount)
            .amount(amount)
            .entryType(EntryType.CREDIT)
            .build();

    ledgerEntryRepository.save(reverseDebitReserved);
    ledgerEntryRepository.save(reverseCreditBuffer);

    transaction.setReferenceId(payload.referenceId());
    transaction.setStatus(status);
    depositRepository.save(transaction);
  }
}
