package per.nonobeam.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeduplicator {

  private static final String KEY_PREFIX = "webhook:event:";

  private final StringRedisTemplate redis;
  private final Duration ttl;

  public WebhookDeduplicator(
      StringRedisTemplate redis, @Value("${hcau.webhook.dedup-ttl:PT24H}") Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  /** Returns true if this eventId is new (first time seen), false if duplicate. */
  public boolean tryAcquire(String eventId) {
    Boolean added = redis.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", ttl);
    return Boolean.TRUE.equals(added);
  }
}
