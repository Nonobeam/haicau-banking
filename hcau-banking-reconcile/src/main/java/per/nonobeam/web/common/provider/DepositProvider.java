package per.nonobeam.web.common.provider;

public interface DepositProvider {
  InitiateResult initiate(long amount, String currency);

  WebhookPayload parseWebhook(String rawBody, String signature);
}
