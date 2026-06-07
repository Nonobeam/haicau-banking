package per.nonobeam.web.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import per.nonobeam.common.dedupe.Idempotency;

@Service
@RequiredArgsConstructor
public class RedisIdempotency implements Idempotency {

  private final RedisClient redisClient;

  private static final String UNLOCK_SCRIPT =
      "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "  return redis.call('del', KEYS[1]) "
          + "else return 0 end";

  @Override
  public void excute(String key, String value, Runnable method) {
    String lockKey = "lock:" + key;

    try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
      RedisCommands<String, String> commands = connection.sync();
      String result = commands.set(lockKey, value, SetArgs.Builder.nx().ex(30));

      if (result == null) {
        throw new RuntimeException("Key already locked: " + key);
      }

      try {
        method.run();
      } finally {
        commands.eval(
            UNLOCK_SCRIPT, io.lettuce.core.ScriptOutputType.INTEGER, new String[] {lockKey}, value);
      }
    }
  }
}
