package per.nonobeam.web.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.AccountResponse;
import per.nonobeam.common.account.AccountState;
import per.nonobeam.common.account.AccountStatus;
import per.nonobeam.common.account.CoaPath;
import per.nonobeam.common.account.CoaPathParser;
import per.nonobeam.common.account.EnableCurrencyRequest;
import per.nonobeam.common.account.LedgerAccountRequest;
import per.nonobeam.common.provider.AbstractProviderAccountPort;
import per.nonobeam.common.provider.ProvisionedAccount;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.LedgerType;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.UserProviderAccount;
import per.nonobeam.web.common.account.Wallet;
import per.nonobeam.web.common.account.WalletStatus;
import per.nonobeam.web.common.ledger.BalanceSnapshot;
import per.nonobeam.web.common.ledger.BalanceSnapshotId;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.BalanceSnapshotRepository;
import per.nonobeam.web.repository.UserProviderAccountRepository;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.WalletRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;
  private final WalletRepository walletRepository;
  private final BalanceSnapshotRepository balanceSnapshotRepository;
  private final UserRepository userRepository;
  private final UserProviderAccountRepository userProviderAccountRepository;
  private final AbstractProviderAccountPort providerAccountPort;
  private final CurrencyValidator currencyValidator;

  @Transactional
  public AccountResponse provisionAccount(LedgerAccountRequest request) {
    Set<String> currencies = dedupe(request.currencies());
    currencyValidator.requireAllActive(currencies);

    User owner = resolveUser(request.ownerId());

    // Idempotency: wallet existence means provisioning already ran.
    var existingWallet = walletRepository.findByCustomerId(owner.getId());
    if (existingWallet.isPresent()) {
      List<Account> ownerAccounts =
          accountRepository.findByOwnerIdAndStatus(owner.getId(), AccountStatus.ACTIVE);
      seedSnapshots(ownerAccounts, currencies);
      Account main =
          accountRepository
              .findByCoaPathAndLedger(
                  CoaPathParser.walletPath(
                      owner.getId(), existingWallet.get().getId(), AccountState.MAIN),
                  LedgerType.SUB)
              .orElseThrow(
                  () ->
                      new ApplicationException(
                          ApplicationErrorCode.ACCOUNT_NOT_FOUND, owner.getId()));
      return mapToResponse(main);
    }

    return provision(owner, currencies);
  }

  @Transactional
  public void enableCurrency(EnableCurrencyRequest request) {
    currencyValidator.requireAllActive(List.of(request.currency()));
    User owner = resolveUser(request.ownerId());
    walletRepository
        .findByCustomerId(owner.getId())
        .orElseThrow(
            () -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, owner.getId()));

    List<Account> ownerAccounts =
        accountRepository.findByOwnerIdAndStatus(owner.getId(), AccountStatus.ACTIVE);
    seedSnapshots(ownerAccounts, Set.of(request.currency()));
  }

  @Transactional(readOnly = true)
  public AccountResponse getAccount(String ownerId) {
    User owner = resolveUser(ownerId);
    Account account =
        walletRepository
            .findByCustomerId(owner.getId())
            .flatMap(
                w ->
                    accountRepository.findByCoaPathAndLedger(
                        CoaPathParser.walletPath(owner.getId(), w.getId(), AccountState.MAIN),
                        LedgerType.SUB))
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, ownerId));
    return mapToResponse(account);
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private Set<String> dedupe(List<String> currencies) {
    Set<String> seen = new LinkedHashSet<>();
    for (String code : currencies) {
      if (!seen.add(code)) {
        throw new ApplicationException(ApplicationErrorCode.DUPLICATE_CURRENCY, code);
      }
    }
    return seen;
  }

  private User resolveUser(String ownerId) {
    User user =
        userRepository
            .findById(ownerId)
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, ownerId));
    if (!"USER".equalsIgnoreCase(user.getUserType().getName())) {
      throw new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, ownerId);
    }
    return user;
  }

  private AccountResponse provision(User owner, Set<String> currencies) {
    // Call back to platform-service via the abstract port to create the provider account.
    ProvisionedAccount provisioned = providerAccountPort.createProviderAccount(owner.getId());

    userProviderAccountRepository.save(
        UserProviderAccount.builder()
            .user(owner)
            .providerCode(provisioned.providerCode())
            .externalAccountId(provisioned.externalAccountId())
            .build());

    Wallet wallet =
        walletRepository.save(
            Wallet.builder().customer(owner).isPrimary(true).status(WalletStatus.ACTIVE).build());

    log.info("Wallet {} created for user {}", wallet.getId(), owner.getId());

    List<Account> newAccounts = new ArrayList<>();
    for (AccountState state :
        List.of(AccountState.MAIN, AccountState.RESERVED, AccountState.CLEARING)) {
      String coaPath = CoaPathParser.walletPath(owner.getId(), wallet.getId(), state);
      newAccounts.add(
          accountRepository.save(
              Account.builder()
                  .owner(owner)
                  .coaPath(coaPath)
                  .ledger(LedgerType.SUB)
                  .walletId(wallet.getId())
                  .status(AccountStatus.ACTIVE)
                  .build()));
      newAccounts.add(
          accountRepository.save(
              Account.builder()
                  .owner(owner)
                  .coaPath(coaPath)
                  .ledger(LedgerType.GL)
                  .walletId(wallet.getId())
                  .status(AccountStatus.ACTIVE)
                  .build()));
    }

    newAccounts.add(
        accountRepository.save(
            Account.builder()
                .owner(owner)
                .coaPath(CoaPathParser.offsetPath(owner.getId()))
                .ledger(LedgerType.GL)
                .status(AccountStatus.ACTIVE)
                .build()));
    newAccounts.add(
        accountRepository.save(
            Account.builder()
                .owner(owner)
                .coaPath(CoaPathParser.revenuePath(owner.getId()))
                .ledger(LedgerType.GL)
                .status(AccountStatus.ACTIVE)
                .build()));

    seedSnapshots(newAccounts, currencies);

    log.info(
        "Wallet provisioning complete: userId={}, walletId={}, {} accounts created, currencies={}",
        owner.getId(),
        wallet.getId(),
        newAccounts.size(),
        currencies);

    return mapToResponse(newAccounts.get(0)); // MAIN SUB is first
  }

  private void seedSnapshots(List<Account> accounts, Set<String> currencies) {
    for (Account account : accounts) {
      for (String currency : currencies) {
        BalanceSnapshotId id = new BalanceSnapshotId(account.getId(), currency);
        if (balanceSnapshotRepository.existsById(id)) {
          continue;
        }
        balanceSnapshotRepository.save(
            BalanceSnapshot.builder().id(id).balance(BigDecimal.ZERO).build());
      }
    }
  }

  private AccountResponse mapToResponse(Account account) {
    String coaPath = account.getCoaPath();
    String accountType = null;
    String state = null;
    if (coaPath != null) {
      try {
        CoaPath parsed = CoaPathParser.parse(coaPath);
        accountType = parsed.accountType().name();
        AccountState parsedState = parsed.state();
        state = parsedState != null ? parsedState.name() : null;
      } catch (IllegalArgumentException ignored) {
      }
    }
    return new AccountResponse(
        account.getId(),
        account.getOwner() != null ? account.getOwner().getId() : null,
        coaPath,
        accountType,
        state,
        account.getWalletId(),
        account.getStatus(),
        account.getCreatedAt());
  }
}
