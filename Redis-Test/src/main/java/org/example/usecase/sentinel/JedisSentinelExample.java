package org.example.usecase.sentinel;

import org.example.config.RedisSettings;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JedisSentinelExample {
    private static final Logger logger = LoggerFactory.getLogger(JedisSentinelExample.class);

    public static void main(String[] args) {
        RedisSettings settings = RedisSettings.load();
        String masterName = "mymaster";

        Set<String> sentinels = new HashSet<>();
        sentinels.add("127.0.0.1:26379");
        sentinels.add("127.0.0.1:26380");
        sentinels.add("127.0.0.1:26381");

        JedisSentinelPool jedisSentinelPool = new JedisSentinelPool(
                masterName,
                sentinels,
                settings.poolConfig(),
                settings.timeoutMillis(),
                settings.timeoutMillis(),
                settings.password(),
                settings.database()
        );

        try {
            while (true) {
                try (Jedis jedis = jedisSentinelPool.getResource()) {
                    int index = new Random().nextInt(100000);
                    String key = "k-" + index;
                    String value = "v-" + index;

                    jedis.set(key, value);
                    logger.info("{} value is {}", key, jedis.get(key));

                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (Exception e) {
                    logger.error("Error in Redis operation: {}", e.getMessage(), e);
                }
            }
        } finally {
            jedisSentinelPool.close();
        }
    }
}
