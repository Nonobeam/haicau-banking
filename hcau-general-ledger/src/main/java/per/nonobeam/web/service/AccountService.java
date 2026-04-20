package per.nonobeam.web.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.account.AccountState;
import per.nonobeam.common.account.CoaPathParser;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.common.account.AccountStatus;
import per.nonobeam.web.common.account.LedgerType;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.Wallet;
import per.nonobeam.web.common.account.WalletProvisioningStatus;
import per.nonobeam.web.common.account.WalletStatus;
import per.nonobeam.web.common.ledger.BalanceSnapshot;
import per.nonobeam.web.common.ledger.BalanceSnapshotId;
import per.nonobeam.web.model.account.AccountResponse;
import per.nonobeam.web.model.account.CreateAccountRequest;
import per.nonobeam.web.repository.AccountRepository;
import per.nonobeam.web.repository.BalanceSnapshotRepository;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.WalletProvisioningStatusRepository;
import per.nonobeam.web.repository.WalletRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

  private static final String DEFAULT_CURRENCY = "USD";

  private final AccountRepository accountRepository;
  private final WalletRepository walletRepository;
  private final BalanceSnapshotRepository balanceSnapshotRepository;
  private final WalletProvisioningStatusRepository walletProvisioningStatusRepository;
  private final UserRepository userRepository;

  @Transactional
  public AccountResponse createAccount(CreateAccountRequest request) {
    User owner = resolveUser(request.getOwnerId());

    // Idempotency: if already provisioned, return the existing main wallet account.
    if (walletProvisioningStatusRepository.existsById(owner.getId())) {
      Account existing =
          walletRepository
              .findByCustomerId(owner.getId())
              .flatMap(
                  w ->
                      accountRepository.findByCoaPath(
                          CoaPathParser.walletPath(owner.getId(), w.getId(), AccountState.MAIN)))
              .orElseThrow(
                  () ->
                      new ApplicationException(
                          ApplicationErrorCode.ACCOUNT_NOT_FOUND, owner.getId()));
      return AccountResponse.mapToResponse(existing);
    }

    return provision(owner);
  }

  @Transactional(readOnly = true)
  public AccountResponse getAccount(String ownerId) {
    User owner = resolveUser(ownerId);
    Account account =
        walletRepository
            .findByCustomerId(owner.getId())
            .flatMap(
                w ->
                    accountRepository.findByCoaPath(
                        CoaPathParser.walletPath(owner.getId(), w.getId(), AccountState.MAIN)))
            .orElseThrow(
                () -> new ApplicationException(ApplicationErrorCode.ACCOUNT_NOT_FOUND, ownerId));
    return AccountResponse.mapToResponse(account);
  }

  // ── Private helpers ────────────────────────────────────────────────────────

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

  /**
   * Full wallet provisioning flow (Decision #47):
   *
   * <ol>
   *   <li>Create wallets row
   *   <li>Create 3 SUB account rows (main, reserved, clearing)
   *   <li>Create per-customer GL accounts (offset, revenue)
   *   <li>Create balance_snapshots for all accounts
   *   <li>Insert wallet_provisioning_status
   * </ol>
   */
  private AccountResponse provision(User owner) {
    // Step 1: create wallets row
    Wallet wallet =
        walletRepository.save(
            Wallet.builder().customer(owner).isPrimary(true).status(WalletStatus.ACTIVE).build());

    log.info("Wallet {} created for user {}", wallet.getId(), owner.getId());

    // Step 2: create 3 SUB accounts
    List<Account> newAccounts = new ArrayList<>();
    for (AccountState state :
        List.of(AccountState.MAIN, AccountState.RESERVED, AccountState.CLEARING)) {
      String coaPath = CoaPathParser.walletPath(owner.getId(), wallet.getId(), state);
      Account account =
          accountRepository.save(
              Account.builder()
                  .owner(owner)
                  .coaPath(coaPath)
                  .ledger(LedgerType.SUB)
                  .walletId(wallet.getId())
                  .status(AccountStatus.ACTIVE)
                  .build());
      newAccounts.add(account);
    }

    // Step 3: create per-customer GL accounts (offset, revenue)
    Account offsetAccount =
        accountRepository.save(
            Account.builder()
                .owner(owner)
                .coaPath(CoaPathParser.offsetPath(owner.getId()))
                .ledger(LedgerType.GL)
                .status(AccountStatus.ACTIVE)
                .build());
    newAccounts.add(offsetAccount);

    Account revenueAccount =
        accountRepository.save(
            Account.builder()
                .owner(owner)
                .coaPath(CoaPathParser.revenuePath(owner.getId()))
                .ledger(LedgerType.GL)
                .status(AccountStatus.ACTIVE)
                .build());
    newAccounts.add(revenueAccount);

    // Step 4: create balance_snapshots for all new accounts
    for (Account account : newAccounts) {
      balanceSnapshotRepository.save(
          BalanceSnapshot.builder()
              .id(new BalanceSnapshotId(account.getId(), DEFAULT_CURRENCY))
              .balance(BigDecimal.ZERO)
              .build());
    }

    // Step 5: insert wallet_provisioning_status
    walletProvisioningStatusRepository.save(
        WalletProvisioningStatus.builder().userId(owner.getId()).build());

    log.info(
        "Wallet provisioning complete: userId={}, walletId={}, {} accounts created",
        owner.getId(),
        wallet.getId(),
        newAccounts.size());

    // Return the main account
    Account mainAccount = newAccounts.get(0); // MAIN is first
    return AccountResponse.mapToResponse(mainAccount);
  }
}
