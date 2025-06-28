package org.example;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        System.out.println("========== Direct Connection ==========");
        // 1️⃣ Direct connection to master (6380)
        try (Jedis jedis = new Jedis("127.0.0.1", 6380)) {
            jedis.set("myKey", "Hello, Redis (direct)!");
            String result = jedis.get("myKey");
            System.out.println("✅ Value from direct connection: " + result);
        } catch (Exception e) {
            System.err.println("❌ Direct connection failed:");
            e.printStackTrace();
        }

        System.out.println("\n========== JedisPool Connection ==========");
        // 2️⃣ JedisPool connection to master (6380)
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(5);
        poolConfig.setMaxIdle(2);

        try (JedisPool jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6380)) {
            try (Jedis pooledJedis = jedisPool.getResource()) {
                pooledJedis.set("name", "Ling (pooled)");
                String value = pooledJedis.get("name");
                System.out.println("✅ Value from pooled connection: " + value);
            }
        } catch (Exception e) {
            System.err.println("❌ JedisPool connection failed:");
            e.printStackTrace();
        }

        System.out.println("\n========== Sentinel Connection ==========");
        // 3️⃣ Sentinel-aware connection
        String masterName = "mymaster";  // Must match your sentinel.conf
        Set<String> sentinels = new HashSet<>();
        sentinels.add("127.0.0.1:26379");  // Sentinel address

        try (JedisSentinelPool sentinelPool = new JedisSentinelPool(masterName, sentinels, poolConfig)) {
            System.out.println("✅ Connected to Sentinel pool");

            // ⭐ Get master address
            HostAndPort currentMaster = sentinelPool.getCurrentHostMaster();
            System.out.println("✅ Discovered master via Sentinel: " + currentMaster.getHost() + ":" + currentMaster.getPort());

            try (Jedis sentinelJedis = sentinelPool.getResource()) {
                System.out.println("✅ Connected to Redis master via Sentinel!");

                sentinelJedis.set("sentinelKey", "Hello from Sentinel-aware client!");
                String value = sentinelJedis.get("sentinelKey");
                System.out.println("✅ Value from Sentinel-aware connection: " + value);
            }
        } catch (Exception e) {
            System.err.println("❌ Sentinel connection failed:");
            e.printStackTrace();
        }
    } // <==== THIS was missing
}
