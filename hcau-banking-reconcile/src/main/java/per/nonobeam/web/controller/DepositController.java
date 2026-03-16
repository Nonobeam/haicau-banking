package per.nonobeam.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.exception.model.res.ApiResp;
import per.nonobeam.service.DepositService;
import per.nonobeam.web.model.deposit.DepositRequest;
import per.nonobeam.web.model.deposit.DepositResponse;

@RestController
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
public class DepositController {

  private final DepositService depositService;

  @PostMapping
  public ResponseEntity<ApiResp<DepositResponse>> initiate(
      @Valid @RequestBody DepositRequest request) {
    return ApiResp.success(depositService.initiate(request));
  }
}
