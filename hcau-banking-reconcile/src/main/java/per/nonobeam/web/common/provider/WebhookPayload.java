package per.nonobeam.web.common.provider;

import java.time.OffsetDateTime;

public record WebhookPayload(
    String sessionId,
    String eventId,
    String referenceId,
    ProviderOutcome outcome,
    long amount,
    String currency,
    OffsetDateTime occurredAt) {}
