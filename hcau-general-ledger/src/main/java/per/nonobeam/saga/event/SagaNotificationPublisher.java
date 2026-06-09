package per.nonobeam.saga.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import per.nonobeam.saga.SagaInstance;

@Component
public class SagaNotificationPublisher {

  private static final Logger log = LoggerFactory.getLogger(SagaNotificationPublisher.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  @Value("${hcau.kafka.topics.notification-email:notification.email}")
  private String emailTopic;

  @Value("${hcau.kafka.topics.notification-telegram:notification.telegram}")
  private String telegramTopic;

  @Value("${internal.channels.platform-service.url:http://localhost:8083}")
  private String platformServiceUrl;

  public SagaNotificationPublisher(
      KafkaTemplate<String, Object> kafkaTemplate,
      ObjectMapper objectMapper,
      RestClient restClient) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

  public void publish(SagaInstance saga, String terminalState) {
    try {
      UserContact contact = resolveContact(saga.getUserId());
      String callbackUrl = extractCallbackUrl(saga.getPayload());

      if (contact.email() != null) {
        publishToEmail(saga, terminalState, contact.email(), callbackUrl);
      }
      if (contact.telegramChatId() != null) {
        publishToTelegram(saga, terminalState, contact.telegramChatId(), callbackUrl);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to publish notification for sagaId={}: {}", saga.getSagaId(), e.getMessage());
    }
  }

  private void publishToEmail(SagaInstance saga, String state, String email, String callbackUrl)
      throws Exception {
    ObjectNode msg = objectMapper.createObjectNode();
    msg.put("toAddress", email);
    msg.put("subject", buildSubject(state, saga.getSagaType()));
    msg.put(
        "content",
        buildEmailContent(state, saga.getSagaType(), saga.getSagaId(), saga.getFailureReason()));
    if (callbackUrl != null) msg.put("callbackUrl", callbackUrl);
    kafkaTemplate.send(emailTopic, saga.getSagaId(), objectMapper.writeValueAsString(msg));
  }

  private void publishToTelegram(SagaInstance saga, String state, String chatId, String callbackUrl)
      throws Exception {
    ObjectNode msg = objectMapper.createObjectNode();
    msg.put("toAddress", chatId);
    msg.put(
        "content",
        buildTelegramContent(state, saga.getSagaType(), saga.getSagaId(), saga.getFailureReason()));
    if (callbackUrl != null) msg.put("callbackUrl", callbackUrl);
    kafkaTemplate.send(telegramTopic, saga.getSagaId(), objectMapper.writeValueAsString(msg));
  }

  private UserContact resolveContact(String userId) {
    try {
      UserContact contact =
          restClient
              .get()
              .uri(platformServiceUrl + "/internal/v1/users/{userId}/contact", userId)
              .retrieve()
              .body(UserContact.class);
      return contact != null ? contact : new UserContact(userId, null, null);
    } catch (Exception e) {
      log.warn("Could not resolve contact for userId={}: {}", userId, e.getMessage());
      return new UserContact(userId, null, null);
    }
  }

  private String extractCallbackUrl(String payload) {
    if (payload == null || payload.isBlank()) return null;
    try {
      JsonNode node = objectMapper.readTree(payload);
      JsonNode cb = node.get("callbackUrl");
      return cb != null && !cb.isNull() ? cb.asText() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private String buildSubject(String state, String sagaType) {
    if ("COMMITTED".equals(state)) return "Transaction completed - " + sagaType;
    if ("COMPENSATED".equals(state)) return "Transaction reversed - " + sagaType;
    if ("DEAD_LETTERED".equals(state)) return "Transaction failed - " + sagaType;
    return "Transaction update - " + sagaType;
  }

  private String buildEmailContent(String state, String sagaType, String sagaId, String reason) {
    if ("COMMITTED".equals(state))
      return String.format(
          "Your %s transaction (ID: %s) has been completed successfully.", sagaType, sagaId);
    if ("COMPENSATED".equals(state))
      return String.format("Your %s transaction (ID: %s) has been reversed.", sagaType, sagaId);
    if ("DEAD_LETTERED".equals(state))
      return String.format(
          "Your %s transaction (ID: %s) could not be completed. Reason: %s. Please contact support.",
          sagaType, sagaId, reason);
    return String.format("Update on your %s transaction (ID: %s).", sagaType, sagaId);
  }

  private String buildTelegramContent(String state, String sagaType, String sagaId, String reason) {
    if ("COMMITTED".equals(state))
      return String.format("[OK] %s completed (ID: %s)", sagaType, sagaId);
    if ("COMPENSATED".equals(state))
      return String.format("[REVERSED] %s reversed (ID: %s)", sagaType, sagaId);
    if ("DEAD_LETTERED".equals(state))
      return String.format("[FAILED] %s failed (ID: %s) - %s", sagaType, sagaId, reason);
    return String.format("[INFO] %s update (ID: %s)", sagaType, sagaId);
  }

  record UserContact(String userId, String email, String telegramChatId) {}
}
