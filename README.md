# Redis Examples

This repo contains Redis notes and four runnable example areas:

- `Cache-Demo`: Spring Boot 3 REST service demonstrating cache penetration, cache stampede (mutex lock), and cache stampede (logical expiration) with Bloom filter, null-value sentinel, and distributed lock.
- `Redis-Test`: Java/Gradle examples for Jedis direct connections, connection pools, Sentinel, and a Spring `RedisTemplate` configuration.
- `Redis-Bloom-Filter`: Java/Maven Bloom filter implementation backed by Redis Cluster bitmaps.
- `Redis-Python`: Python examples for Redis direct/pool/Sentinel connections, Pub/Sub, RediSearch suggestions, and a small Streamlit demo.

Older Redis command notes are kept in `README_bk.md`, and the deeper study notes are in `Redis_Tech.md`.

## Prerequisites

- Java 17 or newer (`Cache-Demo` requires 17; `Redis-Test` works on 11+)
- Gradle wrapper, already included under `Redis-Test`
- Maven (`Cache-Demo` and `Redis-Bloom-Filter`)
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
Cache-Demo/          Spring Boot 3 Maven: cache penetration, stampede, logical expiry
Redis-Test/          Java Gradle sample: Jedis, Sentinel, RedisTemplate config
Redis-Bloom-Filter/  Java Maven Bloom filter module
Redis-Python/        Python Redis scripts and Streamlit demo
README_bk.md         Redis setup and command notes
Redis_Tech.md        Redis concepts and system design notes
sentinel.conf        Example Sentinel config
```

## Run Cache-Demo

`Cache-Demo` is a Spring Boot 3 Maven project. It exposes a REST API that demonstrates three classic Redis caching problems and their solutions side-by-side.

### What it demonstrates

| Problem | Solution | Endpoint prefix |
|---|---|---|
| Cache penetration (non-existent IDs hammering DB) | Bloom filter + null-value sentinel | `/products/pass-through/` |
| Cache stampede — strong consistency | Distributed mutex lock, bounded retry loop | `/products/mutex/`, `/cache/products/mutex/` |
| Cache stampede — high availability | Logical expiration + async background rebuild | `/products/logical/`, `/cache/products/logical/` |

`/products/*` routes use `StringRedisTemplate` (JSON strings).  
`/cache/products/*` routes use `RedisTemplate<String, Object>` (Jackson object serialization).

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` (no auth required by default)

```bash
brew install redis      # macOS
redis-server            # start with defaults
```

### Configuration

Edit `Cache-Demo/src/main/resources/application.yml` to match your Redis setup:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:       # leave blank for no-auth local Redis
      database: 0
```

### Build and run

```bash
cd Cache-Demo
mvn spring-boot:run
```

Or build a jar and run it:

```bash
mvn clean package -DskipTests
java -jar target/cache-demo-0.0.1-SNAPSHOT.jar
```

The server starts on `http://localhost:8080`.

### Sample data

Fifteen products are pre-loaded at startup (IDs 1–15):

| ID | Name | Price |
|---|---|---|
| 1 | iPhone 15 Pro | $1199 |
| 2 | MacBook Pro 14" | $1999 |
| 3 | AirPods Pro | $249 |
| 4 | iPad Air | $749 |
| 5 | Apple Watch Series 9 | $399 |
| 6 | Galaxy S24 Ultra | $1299 |
| … | … | … |
| 15 | Anker USB-C Hub | $49 |

IDs outside this range are unknown — useful for testing cache penetration.

### Test the three strategies

#### 1. Cache penetration — Bloom filter + null-value sentinel

```bash
# ID exists → Bloom passes → Redis miss → DB hit → cached (200)
curl http://localhost:8080/products/pass-through/1

# ID unknown → Bloom blocks instantly → no Redis or DB call (404)
curl http://localhost:8080/products/pass-through/999

# Repeat unknown ID → null-value sentinel in Redis → still fast (404)
curl http://localhost:8080/products/pass-through/999
```

#### 2. Cache stampede — mutex lock

```bash
# First call: cache miss → acquires lock → DB hit → caches result (200)
curl http://localhost:8080/products/mutex/1

# Second call: cache hit, no DB call (200)
curl http://localhost:8080/products/mutex/1

# Same strategy via RedisTemplate<String, Object> serialization
curl http://localhost:8080/cache/products/mutex/1
```

#### 3. Cache stampede — logical expiration

Pre-load before the first read. Reads never hit the DB on the hot path.

```bash
# Pre-load: writes product + logical expiry timestamp into Redis
curl -X POST http://localhost:8080/products/logical/preload/1

# Read: cache hit, returns immediately (200)
curl http://localhost:8080/products/logical/1

# Not pre-loaded → returns 404 (no DB call by design)
curl http://localhost:8080/products/logical/2

# Same strategy via RedisTemplate<String, Object> serialization
curl -X POST http://localhost:8080/cache/products/logical/preload/1
curl http://localhost:8080/cache/products/logical/1
```

#### 4. Write-through update and eviction

```bash
# Update: writes to DB and refreshes both cache keys
curl -X PUT http://localhost:8080/cache/products \
  -H "Content-Type: application/json" \
  -d '{"id":1,"name":"iPhone 15 Pro Max","price":1299.0}'

# Verify: returns updated product (200)
curl http://localhost:8080/products/mutex/1

# Delete: evicts from DB and all cache keys
curl -X DELETE http://localhost:8080/cache/products/1

# Confirm: 404 after eviction
curl http://localhost:8080/products/mutex/1
```

### Full endpoint reference

| Method | URL | Description |
|---|---|---|
| `GET` | `/products/pass-through/{id}` | Bloom filter + null-value sentinel |
| `GET` | `/products/mutex/{id}` | Mutex lock (`StringRedisTemplate`) |
| `GET` | `/products/logical/{id}` | Logical expiry (`StringRedisTemplate`) |
| `POST` | `/products/logical/preload/{id}` | Pre-load logical expiry entry |
| `GET` | `/cache/products/mutex/{id}` | Mutex lock (`RedisTemplate<String,Object>`) |
| `GET` | `/cache/products/logical/{id}` | Logical expiry (`RedisTemplate<String,Object>`) |
| `POST` | `/cache/products/logical/preload/{id}` | Pre-load logical expiry entry |
| `PUT` | `/cache/products` | Write-through update |
| `DELETE` | `/cache/products/{id}` | Evict from all cache keys |

### Inspect Redis keys while running

```bash
redis-cli keys "product:*"          # mutex-strategy keys
redis-cli keys "cache:product:*"    # logical-strategy keys
redis-cli keys "lock:product:*"     # active locks (transient)

redis-cli get "product:1"           # inspect a cached product
redis-cli ttl "product:1"           # check remaining TTL
```

## Run Redis-Test

`Redis-Test` is a plain Java Gradle project. It is not a Spring Boot app, but it includes Spring-compatible Redis examples:

- `RedisConfig`: `RedisTemplate<String, Object>` bean with JSON value serialization.
- `usecase/product/ProductService`: manual RedisTemplate cache-aside product lookup and invalidation.
- `usecase/product/ProductCacheService`: Spring Cache annotation version using `@Cacheable`, `@CachePut`, and `@CacheEvict`.
- `application.yml`: Redis host, password, database, timeout, and pool settings.

Use-case package layout:

```text
Redis-Test/src/main/java/org/example/
├── config/                 Shared Redis YAML, Jedis, and RedisTemplate setup
└── usecase/
    ├── connection/         Direct Jedis and JedisPool examples
    ├── sentinel/           Sentinel-aware write loop example
    └── product/            Product cache use cases
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

It also includes `getByIdSafely`, which adds production-oriented protections:

- Caches null results briefly to reduce cache penetration.
- Uses a short Redis lock to reduce cache breakdown under concurrent misses.
- Adds random TTL jitter to reduce cache avalanche risk.

`ProductMapper` is only an interface in this repo. In a real app, connect it to MyBatis, JPA, or your database layer.

`ProductCacheService.java` demonstrates manual `RedisTemplate` cache-aside with distributed lock protection: null-value caching, UUID lock token with Lua atomic release, bounded retry loop, and TTL jitter.

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
