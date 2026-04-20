# Redis Examples

This repo contains Redis notes and three runnable example areas:

- `Redis-Test`: Java/Gradle examples for Jedis direct connections, connection pools, Sentinel, and a Spring `RedisTemplate` configuration.
- `Redis-Bloom-Filter`: Java/Maven Bloom filter implementation backed by Redis Cluster bitmaps.
- `Redis-Python`: Python examples for Redis direct/pool/Sentinel connections, Pub/Sub, RediSearch suggestions, and a small Streamlit demo.

Older Redis command notes are kept in `README_bk.md`, and the deeper study notes are in `Redis_Tech.md`.

## Prerequisites

- Java 11 or newer
- Gradle wrapper, already included under `Redis-Test`
- Maven
- Python 3.9+
- Redis server or Redis Stack, depending on the example

Install Redis on macOS:

```bash
brew install redis
redis-server --port 6379
redis-cli -p 6379 ping
```

If you run the Java sample exactly as configured, Redis must require password `123456`. Otherwise edit:

```text
Redis-Test/src/main/resources/application.yml
```

For a local Redis instance without auth, remove or blank the `password` value.

## Project Layout

```text
Redis-Test/          Java Gradle sample: Jedis, Sentinel, RedisTemplate config
Redis-Bloom-Filter/  Java Maven Bloom filter module
Redis-Python/        Python Redis scripts and Streamlit demo
README_bk.md         Redis setup and command notes
Redis_Tech.md        Redis concepts and system design notes
sentinel.conf        Example Sentinel config
```

## Run Redis-Test

`Redis-Test` is a plain Java Gradle project. It is not a Spring Boot app, but it includes Spring-compatible Redis examples:

- `RedisConfig`: `RedisTemplate<String, Object>` bean with JSON value serialization.
- `usecase/product/ProductService`: cache-aside product lookup and cache invalidation on update.
- `application.yml`: Redis host, password, database, timeout, and pool settings.

Use-case package layout:

```text
Redis-Test/src/main/java/org/example/
├── config/                 Shared Redis YAML, Jedis, and RedisTemplate setup
└── usecase/
    ├── connection/         Direct Jedis and JedisPool examples
    ├── sentinel/           Sentinel-aware write loop example
    └── product/            RedisTemplate cache-aside product service
```

Configuration:

```text
Redis-Test/src/main/resources/application.yml
```

Build:

```bash
cd Redis-Test
./gradlew test
```

Run the main Jedis example:

```bash
cd Redis-Test
./gradlew run
```

You can also run it from your IDE:

```text
org.example.usecase.connection.RedisConnectionExample
```

Run the Sentinel loop example:

```bash
cd Redis-Test
./gradlew runSentinelExample
```

Or run it from your IDE:

```text
org.example.usecase.sentinel.JedisSentinelExample
```

That example runs continuously, writing random keys through Sentinel using try-with-resources to ensure connections are returned to the pool after each iteration. Stop it manually when done.

### Product Cache Use Case

`Redis-Test/src/main/java/org/example/usecase/product/ProductService.java` demonstrates the common cache-aside pattern:

1. Read `product:{id}` from Redis.
2. If present, return the cached product.
3. If missing, read from `ProductMapper`.
4. Cache the product for 10 minutes.
5. On update, write to the database and delete the Redis key.

`ProductMapper` is only an interface in this repo. In a real app, connect it to MyBatis, JPA, or your database layer.

## Run Redis Sentinel Locally

Start a Redis master:

```bash
redis-server --port 6379
```

Start Sentinel with the included config:

```bash
redis-server sentinel.conf --sentinel
```

Check Sentinel:

```bash
redis-cli -p 26379 info sentinel
```

The Java examples expect Sentinel master name `mymaster`. Keep `sentinel.conf` and the Java code aligned if you rename it.

## Run Redis-Bloom-Filter

Build and test the Maven module:

```bash
cd Redis-Bloom-Filter
mvn test
```

Package it:

```bash
cd Redis-Bloom-Filter
mvn package
```

The Bloom filter code expects a `JedisCluster` instance from application code. The current test source is a usage sketch, not an integration test with a live Redis Cluster.

## Run Redis-Python

Use-case folder layout:

```text
Redis-Python/
├── requirements.txt
└── usecase/
    ├── connection/         Direct Redis, connection pool, and Sentinel sample
    ├── pubsub_search/      Pub/Sub and RediSearch suggestion helpers
    └── recommendation/     Streamlit recommendation demo
```

Create and activate a virtual environment:

```bash
cd Redis-Python
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Run the direct/pool/Sentinel sample:

```bash
python usecase/connection/sentinel.py
```

Override Redis settings with environment variables:

```bash
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 python usecase/connection/sentinel.py
```

Sentinel-specific variables:

```bash
REDIS_SENTINEL_HOST=127.0.0.1 \
REDIS_SENTINEL_PORT=26379 \
REDIS_SENTINEL_MASTER=mymaster \
python usecase/connection/sentinel.py
```

Run the Pub/Sub and suggestion helper sample:

```bash
python usecase/pubsub_search/redis_sampled.py
```

Run the Streamlit recommendation demo:

```bash
streamlit run usecase/recommendation/streamlit_sampled.py
```

The Streamlit demo scans all `item:*` keys and fetches their values in a single pipeline batch before filtering, so it stays fast even with many items. Each value should be JSON with fields like:

```json
{"title": "Payroll setup", "description": "Guide for configuring payroll"}
```

Seed a sample item:

```bash
redis-cli -p 6380 set 'item:1' '{"title":"Payroll setup","description":"Guide for configuring payroll"}'
```

## Useful Verification Commands

Run all available compile checks:

```bash
cd Redis-Test && ./gradlew test
cd ../Redis-Bloom-Filter && mvn test
cd ../Redis-Python && python3 -m py_compile usecase/connection/sentinel.py usecase/pubsub_search/redis_sampled.py usecase/recommendation/streamlit_sampled.py
```

Check Redis:

```bash
redis-cli -p 6379 ping
redis-cli -p 6379 info server
redis-cli -p 6379 info replication
```

## Notes

- `Redis-Test/src/main/resources/application.yml` uses Spring-style keys, but `Redis-Test` currently loads that YAML through `RedisSettings` for the plain Java Jedis examples.
- `RedisConfig` defines a `RedisTemplate<String, Object>` bean for future Spring usage.
- RediSearch suggestion helpers in `Redis-Python/usecase/pubsub_search/redis_sampled.py` require Redis Stack or a Redis deployment with RediSearch available.
