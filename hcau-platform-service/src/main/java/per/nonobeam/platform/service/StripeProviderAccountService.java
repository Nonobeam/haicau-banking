package per.nonobeam.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.param.AccountCreateParams;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import per.nonobeam.common.provider.ProvisionedAccount;
import per.nonobeam.platform.common.stripe.AbstractStripeApiService;
import per.nonobeam.platform.repository.ProviderRepository;

@Slf4j
@Service
public class StripeProviderAccountService extends AbstractStripeApiService {

  private static final String PROVIDER_CODE = "stripe";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProviderRepository providerRepository;
  private final CredentialEncryptionService credentialEncryptionService;
  private final Environment environment;

  public StripeProviderAccountService(
      ProviderRepository providerRepository,
      CredentialEncryptionService credentialEncryptionService,
      Environment environment) {
    super(
        () -> {
          var provider =
              providerRepository
                  .findById(PROVIDER_CODE)
                  .orElseThrow(() -> new IllegalStateException("Stripe provider not found in DB"));
          return extractSecretKey(provider.getCredentials(), credentialEncryptionService);
        });
    this.providerRepository = providerRepository;
    this.credentialEncryptionService = credentialEncryptionService;
    this.environment = environment;
  }

  @PostConstruct
  void validateCredentials() {
    if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
      log.debug("Skipping Stripe credential validation in test profile");
      return;
    }
    var provider =
        providerRepository
            .findById(PROVIDER_CODE)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Stripe provider not found in providers table — run migrations"));
    if (provider.getCredentials() == null || provider.getCredentials().isBlank()) {
      throw new IllegalStateException(
          "Stripe credentials are not configured in providers table. "
              + "Run: CREDENTIALS_ENCRYPTION_KEY=<key> java --source 21"
              + " scripts/EncryptCredential.java");
    }
    extractSecretKey(provider.getCredentials(), credentialEncryptionService);
    log.info("Stripe provider credentials verified");
  }

  public ProvisionedAccount create(String idempotencyKey) {
    try {
      AccountCreateParams params =
          AccountCreateParams.builder()
              .setType(AccountCreateParams.Type.CUSTOM)
              .setCountry("US")
              .setCapabilities(
                  AccountCreateParams.Capabilities.builder()
                      .setTransfers(
                          AccountCreateParams.Capabilities.Transfers.builder()
                              .setRequested(true)
                              .build())
                      .build())
              .build();
      com.stripe.model.Account account =
          execute(
              idempotencyKey, requestOptions -> client().accounts().create(params, requestOptions));
      log.info("Stripe account {} created (key={})", account.getId(), idempotencyKey);
      return new ProvisionedAccount(PROVIDER_CODE, account.getId());
    } catch (StripeException e) {
      log.error("Stripe account creation failed (key={}): {}", idempotencyKey, e.getMessage());
      throw new IllegalStateException("Stripe account creation failed", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static String extractSecretKey(
      String encryptedCredentials, CredentialEncryptionService encryption) {
    try {
      String json = encryption.decrypt(encryptedCredentials);
      Map<String, String> creds = JSON.readValue(json, Map.class);
      String key = creds.get("secret_key");
      if (key == null || key.isBlank()) {
        throw new IllegalStateException("'secret_key' not found in decrypted credentials JSON");
      }
      return key;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse decrypted credentials", e);
    }
  }
}
