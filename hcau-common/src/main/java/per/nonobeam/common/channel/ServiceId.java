package per.nonobeam.common.channel;

import java.util.Arrays;

/**
 * Enumeration of every service that participates in inter-service communication.
 *
 * <p>The 3-digit code is carried in every channel envelope and must match the {@code
 * hcau.service.id} property declared in the service's {@code application.yml}.
 */
public enum ServiceId {
  PLATFORM_SERVICE("100"),
  GENERAL_LEDGER("101"),
  RECONCILE("102");

  /** Regex pattern every service ID must satisfy: exactly 3 decimal digits. */
  public static final String FORMAT = "\\d{3}";

  private final String code;

  ServiceId(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /**
   * Returns the {@link ServiceId} whose code matches {@code code}.
   *
   * @throws IllegalArgumentException if no enum constant has the given code
   */
  public static ServiceId fromCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown service ID: " + code + " (known: " + Arrays.toString(values()) + ")"));
  }

  /**
   * Validates that {@code code} is a well-formed, known service ID.
   *
   * @return the same code, for chaining
   * @throws IllegalArgumentException if the value is null, not 3 digits, or unknown
   */
  public static String validate(String code) {
    if (code == null || !code.matches(FORMAT)) {
      throw new IllegalArgumentException(
          "Service ID must be exactly 3 digits (e.g. 100, 101) but got: " + code);
    }
    fromCode(code);
    return code;
  }
}
