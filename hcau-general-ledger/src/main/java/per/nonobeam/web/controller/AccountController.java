package per.nonobeam.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.common.account.AccountResponse;
import per.nonobeam.common.account.EnableCurrencyRequest;
import per.nonobeam.common.account.LedgerAccountRequest;
import per.nonobeam.web.common.dto.BalanceResponse;
import per.nonobeam.web.common.dto.InternalTransactionRequest;
import per.nonobeam.web.common.dto.InternalTransactionResponse;
import per.nonobeam.web.common.dto.PtsAccountRequest;
import per.nonobeam.web.common.dto.PtsAccountResponse;
import per.nonobeam.web.service.AccountService;
import per.nonobeam.web.service.InternalTransactionService;

@RestController
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;
  private final InternalTransactionService internalTransactionService;

  @GetMapping("/api/v1/accounts/{ownerId}")
  public AccountResponse getAccount(@PathVariable String ownerId) {
    return accountService.getAccount(ownerId);
  }

  @PostMapping("/internal/v1/accounts")
  public AccountResponse provisionAccount(@Valid @RequestBody LedgerAccountRequest request) {
    return accountService.provisionAccount(request);
  }

  @PostMapping("/internal/v1/accounts/currencies")
  public void enableCurrency(@Valid @RequestBody EnableCurrencyRequest request) {
    accountService.enableCurrency(request);
  }

  @PostMapping("/internal/v1/accounts/pts")
  public PtsAccountResponse provisionPtsAccount(@RequestBody PtsAccountRequest request) {
    return internalTransactionService.provisionPtsAccount(request);
  }

  @GetMapping("/internal/v1/accounts/balance")
  public BalanceResponse getBalance(@RequestParam String coaPath, @RequestParam String currency) {
    return internalTransactionService.getBalance(coaPath, currency);
  }

  @PostMapping("/internal/v1/transactions")
  public InternalTransactionResponse postTransaction(
      @RequestBody InternalTransactionRequest request) {
    return internalTransactionService.postTransaction(request);
  }
}
