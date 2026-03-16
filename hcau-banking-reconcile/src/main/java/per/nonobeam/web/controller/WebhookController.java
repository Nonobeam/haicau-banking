package per.nonobeam.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.exception.model.res.ApiResp;
import per.nonobeam.service.DepositWebhookService;
import per.nonobeam.web.common.provider.DepositProvider;

@RestController
@RequestMapping("/api/v1/webhooks/deposit")
@RequiredArgsConstructor
public class WebhookController {

  private final DepositProvider depositProvider;
  private final DepositWebhookService depositWebhookService;

  @PostMapping
  public ResponseEntity<ApiResp<String>> handle(
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Signature", required = false) String signature) {
    var payload = depositProvider.parseWebhook(rawBody, signature);
    depositWebhookService.handle(payload);
    return ApiResp.success("OK");
  }
}
