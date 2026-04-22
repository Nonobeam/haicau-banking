package per.nonobeam.platform.service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CredentialEncryptionService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec secretKey;

  public CredentialEncryptionService(@Value("${credentials.encryption-key}") String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException(
          "credentials.encryption-key must be a Base64-encoded 256-bit (32-byte) key");
    }
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

      byte[] payload = new byte[IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
      System.arraycopy(ciphertext, 0, payload, IV_LENGTH, ciphertext.length);

      return Base64.getEncoder().encodeToString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt credential", e);
    }
  }

  public String decrypt(String encryptedBase64) {
    try {
      byte[] payload = Base64.getDecoder().decode(encryptedBase64);
      byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
      byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

      return new String(cipher.doFinal(ciphertext));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt credential", e);
    }
  }
}
