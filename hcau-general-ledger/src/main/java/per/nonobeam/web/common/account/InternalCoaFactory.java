package per.nonobeam.web.common.account;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;

public final class InternalCoaFactory {

  private static final Pattern FORMAT =
      Pattern.compile(
          "^coa:v1:users:(?<owner>[a-f0-9\-]{36}):domain:(?<domain>[A-Z_]+):wallet:(?<currency>[A-Z]+):(?<bucket>[A-Z_]+)$");

  private InternalCoaFactory() {}

  public static String build(UUID ownerId, String domainName, String currency, BucketEnum bucket) {
    String normalizedCurrency = normalizeCurrency(currency);
    String normalizedDomain = normalizeDomain(domainName);
    return String.format(
        Locale.ROOT,
        "coa:v1:users:%s:domain:%s:wallet:%s:%s",
        ownerId,
        normalizedDomain,
        normalizedCurrency,
        bucket.name());
  }

  public static InternalCoa parse(String internalCoa) {
    Matcher matcher = FORMAT.matcher(internalCoa);
    if (!matcher.matches()) {
      throw new ApplicationException(
          ApplicationErrorCode.INTERNAL_SERVER_ERROR, "Invalid internal COA format");
    }

    UUID ownerId = UUID.fromString(matcher.group("owner"));
    String domain = matcher.group("domain");
    String currency = matcher.group("currency");
    BucketEnum bucket = BucketEnum.valueOf(matcher.group("bucket"));
    return new InternalCoa(ownerId, domain, currency, bucket);
  }

  private static String normalizeCurrency(String currency) {
    if (currency == null || currency.isBlank()) {
      throw new ApplicationException(ApplicationErrorCode.CURRENCY_NOT_SUPPORTED, currency);
    }
    return currency.toUpperCase(Locale.ROOT);
  }

  private static String normalizeDomain(String domainName) {
    if (domainName == null || domainName.isBlank()) {
      throw new ApplicationException(ApplicationErrorCode.DOMAIN_NOT_FOUND, "domain");
    }
    return domainName.toUpperCase(Locale.ROOT);
  }
}
