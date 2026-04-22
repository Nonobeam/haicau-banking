import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts Stripe credentials using AES-256-GCM — same scheme as CredentialEncryptionService.
 * Prompts for the raw Stripe secret key, wraps it in JSON, and encrypts the whole blob.
 *
 * Usage:
 *   CREDENTIALS_ENCRYPTION_KEY=<base64-key> java --source 21 scripts/EncryptCredential.java
 *
 * Generate a key with:  openssl rand -base64 32
 */
class EncryptCredential {

  public static void main(String[] args) throws Exception {
    String base64Key = System.getenv("CREDENTIALS_ENCRYPTION_KEY");
    if (base64Key == null || base64Key.isBlank()) {
      System.err.println("Error: CREDENTIALS_ENCRYPTION_KEY is not set.");
      System.err.println("       Generate one with: openssl rand -base64 32");
      System.exit(1);
    }

    byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
    if (keyBytes.length != 32) {
      System.err.println("Error: CREDENTIALS_ENCRYPTION_KEY must be a Base64-encoded 32-byte key.");
      System.exit(1);
    }

    var console = System.console();
    String stripeKey;
    if (console != null) {
      stripeKey = new String(console.readPassword("Enter Stripe secret key (sk_live_... or sk_test_...): "));
    } else {
      stripeKey = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    if (stripeKey == null || stripeKey.isBlank()) {
      System.err.println("Error: no key provided.");
      System.exit(1);
    }

    String plaintext = "{\"secret_key\":\"" + stripeKey.trim() + "\"}";

    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
    byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

    byte[] payload = new byte[12 + ciphertext.length];
    System.arraycopy(iv, 0, payload, 0, 12);
    System.arraycopy(ciphertext, 0, payload, 12, ciphertext.length);

    String encrypted = Base64.getEncoder().encodeToString(payload);
    System.err.println();
    System.err.println("SQL to update the providers table:");
    System.err.println("  UPDATE \"platform-service\".providers");
    System.err.println("  SET credentials = '" + encrypted + "'");
    System.err.println("  WHERE code = 'stripe';");
  }
}
