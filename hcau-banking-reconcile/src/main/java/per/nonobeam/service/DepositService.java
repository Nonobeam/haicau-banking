package per.nonobeam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.BucketEnum;
import per.nonobeam.common.account.User;
import per.nonobeam.common.config.ExternalProvider;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntry;
import per.nonobeam.common.ledger.Transaction;
import per.nonobeam.common.ledger.TransactionStatus;
import per.nonobeam.common.ledger.TransactionType;
import per.nonobeam.config.CorrelationIdFilter;
import per.nonobeam.config.UlidGenerator;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.repository.AccountRepository;
import per.nonobeam.repository.CommonUserRepository;
import per.nonobeam.repository.DepositRepository;
import per.nonobeam.repository.ExternalProviderRepository;
import per.nonobeam.repository.LedgerEntryRepository;
import per.nonobeam.repository.TransactionTypeRepository;
import per.nonobeam.web.common.provider.DepositProvider;
import per.nonobeam.web.model.deposit.DepositRequest;
import per.nonobeam.web.model.deposit.DepositResponse;

@Service
@RequiredArgsConstructor
public class DepositService {

  private static final List<String> SUPPORTED_CURRENCIES = List.of("USD");

  private final CommonUserRepository userRepository;
  private final AccountRepository accountRepository;
  private final DepositRepository depositRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final ExternalProviderRepository externalProviderRepository;
  private final DepositProvider depositProvider;
  private final ObjectMapper objectMapper;

  public DepositResponse initiate(DepositRequest request) {
    validateRequest(request);

    String currency = request.getCurrency().toUpperCase();
    long amount = request.getAmount();

    var initiateResult = depositProvider.initiate(amount, currency);

    return persistInitiation(
        request.getUserId(),
        currency,
        amount,
        initiateResult.sessionId(),
        initiateResult.redirectUrl());
  }

  private void validateRequest(DepositRequest request) {
    if (request.getAmount() == null || request.getAmount() <= 0) {
      throw new ApplicationException(ApplicationErrorCode.INVALID_REQUEST_PARAMETER, "amount");
    }

    String currency = request.getCurrency() == null ? "" : request.getCurrency().toUpperCase();
    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      throw new ApplicationException(ApplicationErrorCode.CURRENCY_NOT_SUPPORTED, currency);
    }

    User user =
        userRepository
            .findById(request.getUserId())
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.USER_NOT_FOUND, request.getUserId()));

    if (!"USER".equalsIgnoreCase(user.getUserType().getName())) {
      throw new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, request.getUserId());
    }

    Account reserved =
        accountRepository
            .findAccountByOwnerAndCurrencyAndBucketName(
                user.getId(), currency, BucketEnum.RESERVED.name())
            .orElseThrow(
                () ->
                    new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, user.getId()));

    if (reserved.getStatus() != AccountStatus.ACTIVE) {
      throw new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_ACTIVE, reserved.getId());
    }

    // TODO: enforce max deposit limit by user tier.
  }

  @Transactional
  protected DepositResponse persistInitiation(
      String userId, String currency, long amount, String sessionId, String redirectUrl) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, userId));

    Account reserved =
        accountRepository
            .findByInternalCoa(
                InternalCoaFactory.build(
                    UUID.fromString(userId), "FIAT", currency, BucketEnum.RESERVED))
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, userId));

    Account buffer =
        accountRepository
            .findByInternalCoa(
                InternalCoaFactory.build(
                    UUID.fromString("user_00000000000000000000000000SYSTEM"),
                    "FIAT",
                    currency,
                    BucketEnum.AVAILABLE))
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.ACCOUNT_NOT_FOUND, "SYSTEM_BUFFER"));

    TransactionType inboundDepositType =
        transactionTypeRepository
            .findByName("INBOUND_DEPOSIT")
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.INVALID_REQUEST, "INBOUND_DEPOSIT"));

    ExternalProvider provider =
        externalProviderRepository
            .findByName("STUB")
            .orElseGet(
                () ->
                    externalProviderRepository.save(
                        ExternalProvider.builder()
                            .id("prov_stub_default")
                            .name("STUB")
                            .type("PAYMENT_PROCESSOR")
                            .config("{}")
                            .build()));

    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UlidGenerator.generateCorrelationId();
    }

    String metadata;
    try {
      metadata = objectMapper.writeValueAsString(java.util.Map.of("sessionId", sessionId));
    } catch (Exception ex) {
      throw new ApplicationException(ApplicationErrorCode.INTERNAL_SERVER_ERROR, "metadata");
    }

    Transaction transaction =
        Transaction.builder()
            .id(UlidGenerator.generateTransactionId())
            .idempotencyKey(correlationId + "_" + sessionId)
            .transactionType(inboundDepositType)
            .status(TransactionStatus.PENDING)
            .actorId(user.getId())
            .correlationId(correlationId)
            .sourceService("hcau-banking-reconcile")
            .providerId(provider.getId())
            .metadata(metadata)
            .build();

    depositRepository.save(transaction);

    LedgerEntry userCredit =
        LedgerEntry.builder()
            .id(UlidGenerator.generateEntryId())
            .transaction(transaction)
            .account(reserved)
            .amount(amount)
            .entryType(EntryType.CREDIT)
            .build();

    LedgerEntry bufferDebit =
        LedgerEntry.builder()
            .id(UlidGenerator.generateEntryId())
            .transaction(transaction)
            .account(buffer)
            .amount(amount)
            .entryType(EntryType.DEBIT)
            .build();

    ledgerEntryRepository.save(userCredit);
    ledgerEntryRepository.save(bufferDebit);

    return new DepositResponse(
        transaction.getId(), sessionId, redirectUrl, transaction.getStatus().name());
  }
}
