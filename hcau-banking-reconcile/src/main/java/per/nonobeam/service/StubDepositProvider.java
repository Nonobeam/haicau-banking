package per.nonobeam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import per.nonobeam.config.UuidV7Generator;
import per.nonobeam.web.common.provider.DepositProvider;
import per.nonobeam.web.common.provider.InitiateResult;
import per.nonobeam.web.common.provider.ProviderOutcome;
import per.nonobeam.web.common.provider.WebhookPayload;

@Component
@ConditionalOnProperty(name = "stub.enabled", havingValue = "true")
@RequiredArgsConstructor
public class StubDepositProvider implements DepositProvider {

  private final ObjectMapper objectMapper;
  private final Map<String, StubSession> sessions = new ConcurrentHashMap<>();

  @Override
  public InitiateResult initiate(long amount, String currency) {
    String sessionId = "stub_session_" + UuidV7Generator.generate("ses");
    String redirectUrl = "/api/v1/stub/deposit/pay?session=" + sessionId;
    sessions.put(sessionId, new StubSession(sessionId, amount, currency, OffsetDateTime.now()));
    return new InitiateResult(sessionId, redirectUrl);
  }

  @Override
  public WebhookPayload parseWebhook(String rawBody, String signature) {
    validateSignature(signature);
    try {
      StubWebhookRaw raw = objectMapper.readValue(rawBody, StubWebhookRaw.class);
      return new WebhookPayload(
          raw.sessionId(),
          raw.eventId(),
          raw.referenceId(),
          ProviderOutcome.valueOf(raw.outcome()),
          raw.amount(),
          raw.currency(),
          raw.occurredAt() == null ? OffsetDateTime.now() : raw.occurredAt());
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid stub webhook payload", ex);
    }
  }

  private void validateSignature(String signature) {
    // Stub implementation intentionally skips cryptographic validation.
  }

  public StubSession getSession(String sessionId) {
    return sessions.get(sessionId);
  }

  public record StubSession(
      String sessionId, long amount, String currency, OffsetDateTime createdAt) {}

  private record StubWebhookRaw(
      String sessionId,
      String eventId,
      String referenceId,
      String outcome,
      long amount,
      String currency,
      OffsetDateTime occurredAt) {}
}
