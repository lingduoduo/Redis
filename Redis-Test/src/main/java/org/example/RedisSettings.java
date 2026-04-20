package org.example;

import org.yaml.snakeyaml.Yaml;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPoolConfig;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public class RedisSettings {
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int timeoutMillis;
    private final int maxActive;
    private final int maxIdle;
    private final int minIdle;
    private final long maxWaitMillis;

    private RedisSettings(
            String host,
            int port,
            String password,
            int database,
            int timeoutMillis,
            int maxActive,
            int maxIdle,
            int minIdle,
            long maxWaitMillis
    ) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
        this.timeoutMillis = timeoutMillis;
        this.maxActive = maxActive;
        this.maxIdle = maxIdle;
        this.minIdle = minIdle;
        this.maxWaitMillis = maxWaitMillis;
    }

    public static RedisSettings load() {
        try (InputStream input = RedisSettings.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (input == null) {
                throw new IllegalStateException("application.yml was not found on the classpath");
            }

            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> redis = section(section(section(root, "spring"), "data"), "redis");
            Map<String, Object> pool = section(section(redis, "lettuce"), "pool");

            return new RedisSettings(
                    stringValue(redis, "host", "127.0.0.1"),
                    intValue(redis, "port", 6379),
                    stringValue(redis, "password", null),
                    intValue(redis, "database", 0),
                    durationMillis(redis, "timeout", 3000),
                    intValue(pool, "max-active", 32),
                    intValue(pool, "max-idle", 16),
                    intValue(pool, "min-idle", 4),
                    durationMillis(pool, "max-wait", 2000)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Redis settings from application.yml", e);
        }
    }

    public JedisClientConfig clientConfig() {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .database(database)
                .connectionTimeoutMillis(timeoutMillis)
                .socketTimeoutMillis(timeoutMillis);

        if (password != null && !password.isEmpty()) {
            builder.password(password);
        }

        return builder.build();
    }

    public JedisPoolConfig poolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(maxActive);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        return config;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String password() {
        return password;
    }

    public int database() {
        return database;
    }

    public int timeoutMillis() {
        return timeoutMillis;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Missing configuration section: " + key);
        }
        return (Map<String, Object>) value;
    }

    private static String stringValue(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int intValue(Map<String, Object> source, String key, int defaultValue) {
        Object value = source.get(key);
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private static int durationMillis(Map<String, Object> source, String key, int defaultValue) {
        Object value = source.get(key);
        if (value == null) {
            return defaultValue;
        }

        String raw = String.valueOf(value).trim().toLowerCase();
        if (raw.endsWith("ms")) {
            return Integer.parseInt(raw.substring(0, raw.length() - 2));
        }
        if (raw.endsWith("s")) {
            return Math.toIntExact(Duration.ofSeconds(Long.parseLong(raw.substring(0, raw.length() - 1))).toMillis());
        }
        return Integer.parseInt(raw);
    }
}
