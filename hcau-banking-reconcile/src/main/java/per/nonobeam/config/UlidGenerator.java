package per.nonobeam.config;

import com.github.f4b6a3.ulid.UlidCreator;

public final class UlidGenerator {

  private UlidGenerator() {}

  public static String generate(String prefix) {
    String ulid = UlidCreator.getUlid().toLowerCase();
    return prefix + "_" + ulid;
  }

  public static String generateTransactionId() {
    return generate("trnx");
  }

  public static String generateAccountId() {
    return generate("acct");
  }

  public static String generateEntryId() {
    return generate("entr");
  }

  public static String generateUserId() {
    return generate("user");
  }

  public static String generateCorrelationId() {
    return generate("req");
  }
}
