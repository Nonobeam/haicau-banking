package per.nonobeam.config;

import com.github.f4b6a3.uuid.UuidCreator;

public final class UuidV7Generator {

  private UuidV7Generator() {}

  public static String generate(String prefix) {
    String uuidV7 = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
    return prefix + "_" + uuidV7;
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
