package per.nonobeam.config;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountDeduplicator {

  private static final String KEY_PREFIX = "account:provision:";
  private static final String CURRENCIES_PREFIX = "account:currencies:";

  private final StringRedisTemplate redis;
  private final Duration ttl;

  public AccountDeduplicator(
      StringRedisTemplate redis, @Value("${hcau.account.dedup-ttl:PT10M}") Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  /** Returns true if this ownerId is new (first in-flight), false if a duplicate request. */
  public boolean tryAcquire(String ownerId) {
    Boolean added = redis.opsForValue().setIfAbsent(KEY_PREFIX + ownerId, "1", ttl);
    return Boolean.TRUE.equals(added);
  }

  public void release(String ownerId) {
    redis.delete(KEY_PREFIX + ownerId);
  }

  /**
   * Returns only the currency codes not yet seen for this owner, using a Redis Set as the dedup
   * store. Codes already present in the set are silently dropped.
   */
  public Set<String> uniqueCurrencies(String ownerId, Collection<String> currencies) {
    String key = CURRENCIES_PREFIX + ownerId;
    Set<String> unique = new LinkedHashSet<>();
    for (String code : currencies) {
      Long added = redis.opsForSet().add(key, code);
      if (added != null && added > 0) {
        unique.add(code);
      }
    }
    redis.expire(key, ttl);
    return unique;
  }
}
