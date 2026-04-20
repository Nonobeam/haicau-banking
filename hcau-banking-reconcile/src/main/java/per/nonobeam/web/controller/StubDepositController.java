package per.nonobeam.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import per.nonobeam.config.UuidV7Generator;
import per.nonobeam.exception.model.res.ApiResp;
import per.nonobeam.service.StubDepositProvider;
import per.nonobeam.web.common.provider.ProviderOutcome;
import per.nonobeam.web.model.deposit.StubExpireRequest;
import per.nonobeam.web.model.deposit.StubInitiateRequest;
import per.nonobeam.web.model.deposit.StubTriggerRequest;

@RestController
@RequestMapping("/api/v1/stub/deposit")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stub.enabled", havingValue = "true")
public class StubDepositController {

  private final StubDepositProvider stubDepositProvider;
  private final ObjectMapper objectMapper;

  @PostMapping("/initiate")
  public ResponseEntity<ApiResp<Object>> initiate(@Valid @RequestBody StubInitiateRequest request) {
    var result = stubDepositProvider.initiate(request.amount(), request.currency());
    return ApiResp.success(
        Map.of("sessionId", result.sessionId(), "redirectUrl", result.redirectUrl()));
  }

  @PostMapping("/trigger")
  public ResponseEntity<ApiResp<String>> trigger(@Valid @RequestBody StubTriggerRequest request) {
    var session = stubDepositProvider.getSession(request.sessionId());
    if (session == null) {
      return ApiResp.success("SESSION_NOT_FOUND");
    }

    ProviderOutcome outcome = ProviderOutcome.valueOf(request.outcome().toUpperCase());
    postWebhook(session.sessionId(), outcome, session.amount(), session.currency());
    return ApiResp.success("OK");
  }

  @PostMapping("/expire")
  public ResponseEntity<ApiResp<String>> expire(@Valid @RequestBody StubExpireRequest request) {
    var session = stubDepositProvider.getSession(request.sessionId());
    if (session == null) {
      return ApiResp.success("SESSION_NOT_FOUND");
    }

    postWebhook(session.sessionId(), ProviderOutcome.EXPIRED, session.amount(), session.currency());
    return ApiResp.success("OK");
  }

  private void postWebhook(
      String sessionId, ProviderOutcome outcome, long amount, String currency) {
    try {
      String body =
          objectMapper.writeValueAsString(
              Map.of(
                  "sessionId",
                  sessionId,
                  "eventId",
                  UuidV7Generator.generate("evt"),
                  "referenceId",
                  UuidV7Generator.generate("pay"),
                  "outcome",
                  outcome.name(),
                  "amount",
                  amount,
                  "currency",
                  currency,
                  "occurredAt",
                  OffsetDateTime.now()));

      WebClient.create("http://localhost:8080")
          .post()
          .uri("/api/v1/webhooks/deposit")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .toBodilessEntity()
          .block();
    } catch (Exception ex) {
      throw new RuntimeException("Failed to post stub webhook", ex);
    }
  }
}
