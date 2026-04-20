package org.example;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;

import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        RedisSettings settings = RedisSettings.load();

        System.out.println("========== Direct Connection ==========");
        try (Jedis jedis = new Jedis(new HostAndPort(settings.host(), settings.port()), settings.clientConfig())) {
            jedis.set("myKey", "Hello, Redis (direct)!");
            String result = jedis.get("myKey");
            System.out.println("Value from direct connection: " + result);
        } catch (Exception e) {
            System.err.println("Direct connection failed:");
            e.printStackTrace();
        }

        System.out.println("\n========== JedisPool Connection ==========");
        try (JedisPool jedisPool = new JedisPool(
                settings.poolConfig(),
                new HostAndPort(settings.host(), settings.port()),
                settings.clientConfig()
        )) {
            try (Jedis pooledJedis = jedisPool.getResource()) {
                pooledJedis.set("name", "Ling (pooled)");
                String value = pooledJedis.get("name");
                System.out.println("Value from pooled connection: " + value);
            }
        } catch (Exception e) {
            System.err.println("JedisPool connection failed:");
            e.printStackTrace();
        }

        System.out.println("\n========== Sentinel Connection ==========");
        String masterName = "mymaster";
        Set<String> sentinels = new HashSet<>();
        sentinels.add("127.0.0.1:26379");

        try (JedisSentinelPool sentinelPool = new JedisSentinelPool(
                masterName,
                sentinels,
                settings.poolConfig(),
                settings.timeoutMillis(),
                settings.timeoutMillis(),
                settings.password(),
                settings.database()
        )) {
            System.out.println("Connected to Sentinel pool");

            HostAndPort currentMaster = sentinelPool.getCurrentHostMaster();
            System.out.println("Discovered master via Sentinel: " + currentMaster.getHost() + ":" + currentMaster.getPort());

            try (Jedis sentinelJedis = sentinelPool.getResource()) {
                System.out.println("Connected to Redis master via Sentinel!");

                sentinelJedis.set("sentinelKey", "Hello from Sentinel-aware client!");
                String value = sentinelJedis.get("sentinelKey");
                System.out.println("Value from Sentinel-aware connection: " + value);
            }
        } catch (Exception e) {
            System.err.println("Sentinel connection failed:");
            e.printStackTrace();
        }
    }
}
