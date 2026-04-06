package per.nonobeam.web.common.account;

import java.util.UUID;

public record InternalCoa(UUID ownerId, String domainName, String currency, BucketEnum bucket) {

  public String value() {
    return InternalCoaFactory.build(ownerId, domainName, currency, bucket);
  }
}
