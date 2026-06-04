package per.nonobeam.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.config.WebhookDeduplicator;
import per.nonobeam.exception.model.res.ApiResp;
import per.nonobeam.service.DepositWebhookService;
import per.nonobeam.web.common.provider.DepositProvider;
import per.nonobeam.web.common.provider.WebhookPayload;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/deposit")
@RequiredArgsConstructor
public class WebhookController {

  private final DepositProvider depositProvider;
  private final DepositWebhookService depositWebhookService;
  private final WebhookDeduplicator deduplicator;

  @PostMapping
  public ResponseEntity<ApiResp<String>> handle(
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Signature", required = false) String signature) {
    WebhookPayload payload = depositProvider.parseWebhook(rawBody, signature);
    if (!deduplicator.tryAcquire(payload.eventId())) {
      log.info("Duplicate webhook eventId={}, skipping", payload.eventId());
      return ApiResp.success("OK");
    }
    depositWebhookService.handle(payload);
    return ApiResp.success("OK");
  }
}
