package per.nonobeam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.Account;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.CoaPathParser;
import per.nonobeam.common.account.User;
import per.nonobeam.common.config.ExternalProvider;
import per.nonobeam.common.ledger.EntryType;
import per.nonobeam.common.ledger.LedgerEntryWriter;
import per.nonobeam.common.ledger.LedgerEntryWriter.LedgerEntrySpec;
import per.nonobeam.common.ledger.Transaction;
import per.nonobeam.common.ledger.TransactionStatus;
import per.nonobeam.common.ledger.TransactionType;
import per.nonobeam.config.CorrelationIdFilter;
import per.nonobeam.config.UuidV7Generator;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.repository.AccountRepository;
import per.nonobeam.repository.CommonUserRepository;
import per.nonobeam.repository.DepositRepository;
import per.nonobeam.repository.ExternalProviderRepository;
import per.nonobeam.repository.TransactionTypeRepository;
import per.nonobeam.web.common.provider.DepositProvider;
import per.nonobeam.web.model.deposit.DepositRequest;
import per.nonobeam.web.model.deposit.DepositResponse;

/**
 * Handles deposit initiation (Stage 1).
 *
 * <p>Stage 1 entries (DEPOSIT_RECEIVABLE):
 *
 * <ul>
 *   <li>GL DEBIT: receivable:counterparty:stripe
 *   <li>GL CREDIT: wallet:control:clearing
 *   <li>SUB CREDIT: wallet:{customer_id}:{wallet_id}:clearing
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DepositService {

  private static final List<String> SUPPORTED_CURRENCIES = List.of("USD");
  private static final String PROVIDER_NAME = "stripe";

  private final CommonUserRepository userRepository;
  private final AccountRepository accountRepository;
  private final DepositRepository depositRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final ExternalProviderRepository externalProviderRepository;
  private final LedgerEntryWriter ledgerEntryWriter;
  private final DepositProvider depositProvider;
  private final ObjectMapper objectMapper;

  public DepositResponse initiate(DepositRequest request) {
    validateRequest(request);

    String currency = request.getCurrency().toUpperCase();
    BigDecimal amount = BigDecimal.valueOf(request.getAmount());

    var initiateResult = depositProvider.initiate(request.getAmount(), currency);

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

    Account clearing =
        accountRepository
            .findClearingAccountByOwner(user.getId())
            .orElseThrow(
                () ->
                    new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, user.getId()));

    if (clearing.getStatus() != AccountStatus.ACTIVE) {
      throw new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_ACTIVE, clearing.getId());
    }
  }

  @Transactional
  protected DepositResponse persistInitiation(
      String userId, String currency, BigDecimal amount, String sessionId, String redirectUrl) {

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, userId));

    // Resolve GL accounts
    final Account receivable =
        accountRepository
            .findByCoaPath(CoaPathParser.receivablePath(PROVIDER_NAME))
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.ACCOUNT_NOT_FOUND, "receivable:counterparty:stripe"));

    final Account walletControlClearing =
        accountRepository
            .findByCoaPath(
                CoaPathParser.walletControlPath(per.nonobeam.common.account.AccountState.CLEARING))
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.ACCOUNT_NOT_FOUND, "wallet:control:clearing"));

    // Resolve customer clearing account
    final Account customerClearing =
        accountRepository
            .findClearingAccountByOwner(userId)
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, userId));

    TransactionType depositReceivableType =
        transactionTypeRepository
            .findByName("DEPOSIT_RECEIVABLE")
            .orElseThrow(
                () ->
                    new ApplicationException(
                        ApplicationErrorCode.INVALID_REQUEST, "DEPOSIT_RECEIVABLE"));

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

    String traceId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    if (traceId == null || traceId.isBlank()) {
      traceId = UuidV7Generator.generateCorrelationId();
    }

    String metadata;
    try {
      metadata = objectMapper.writeValueAsString(java.util.Map.of("sessionId", sessionId));
    } catch (Exception ex) {
      throw new ApplicationException(ApplicationErrorCode.INTERNAL_SERVER_ERROR, "metadata");
    }

    Transaction transaction =
        Transaction.builder()
            .idempotencyKey(traceId + "_" + sessionId)
            .transactionType(depositReceivableType)
            .status(TransactionStatus.PENDING)
            .actorId(user.getId())
            .traceId(traceId)
            .sourceService("hcau-banking-reconcile")
            .providerId(provider.getId())
            .metadata(metadata)
            .build();

    depositRepository.save(transaction);

    // Stage 1 entries: GL DEBIT receivable, GL CREDIT wallet:control:clearing,
    // SUB CREDIT customer clearing (cross-ledger class a)
    ledgerEntryWriter.write(
        transaction,
        List.of(
            new LedgerEntrySpec(receivable.getId(), EntryType.DEBIT, amount, currency),
            new LedgerEntrySpec(walletControlClearing.getId(), EntryType.CREDIT, amount, currency),
            new LedgerEntrySpec(customerClearing.getId(), EntryType.CREDIT, amount, currency)));

    return new DepositResponse(
        transaction.getId(), sessionId, redirectUrl, transaction.getStatus().name());
  }
}
