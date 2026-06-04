package per.nonobeam.web.service;

import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties.Jedis;
import org.springframework.stereotype.Service;

import io.lettuce.core.RedisClient;
import lombok.RequiredArgsConstructor;
import per.nonobeam.common.dedupe.Idempotency;;

@Service
@RequiredArgsConstructor
public class RedisIdempotency implements Idempotency {

  private final RedisClient redisClient;

  private static final String UNLOCK_SCRIPT =
      "if redis.call('get', KEYS[1]) == ARGV[1] then " +
      "  return redis.call('del', KEYS[1]) " +
      "else return 0 end";

  public void excute(String key, String value, Runnable method) {
    String lockKey = "lock:" + key;
    String value = UUID.randomUUID().toString();

    String result = redisClient.set(lockKey, value, "NX", "EX", 30);

    if (result == null) {
      throw new RuntimeException("Key already locked: " + key);
    }

    try {
      method.run();
    } finally {
      redisClient.eval(UNLOCK_SCRIPT, 
          Collections.singletonList(lockKey), 
          Collections.singletonList(value));
    }
  }
}
