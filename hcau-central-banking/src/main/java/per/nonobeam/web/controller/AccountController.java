package per.nonobeam.web.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.web.common.account.Account;
import per.nonobeam.web.model.account.AccountResponse;
import per.nonobeam.web.model.account.CreateAccountRequest;
import per.nonobeam.web.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @PostMapping
  public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
    return accountService.createAccount(request);
  }

  @GetMapping("/{id}")
  public Account getAccount(@PathVariable UUID id) {
    return accountService.getAccount(id);
  }
}
