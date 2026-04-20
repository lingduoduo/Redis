# Redis Examples

This repo contains Redis notes and six runnable example areas:

- `Redis-Cache-Demo`: Spring Boot 3 REST service demonstrating cache penetration, cache stampede (mutex lock), and cache stampede (logical expiration) with Bloom filter, null-value sentinel, and distributed lock.
- `Redis-RateLimit-Demo`: Spring Boot 3 REST service demonstrating Redis + Lua sliding-window rate limiting with annotation-based AOP.
- `Redis-Test`: Java/Gradle examples for Jedis direct connections, connection pools, Sentinel, and a Spring `RedisTemplate` configuration.
- `Redis-Lock-Demo`: Spring Boot 3 REST service demonstrating custom Redis locks and Redisson `RLock`.
- `Redis-Bloom-Filter`: Java/Maven Bloom filter implementation backed by Redis Cluster bitmaps.
- `Redis-Python`: Python examples for Redis direct/pool/Sentinel connections, Pub/Sub, RediSearch suggestions, and a small Streamlit demo.

Older Redis command notes are kept in `README_bk.md`, and the deeper study notes are in `Redis_Tech.md`.

## Prerequisites

- Java 17 or newer (`Redis-Cache-Demo` requires 17; `Redis-Test` works on 11+)
- Gradle wrapper, already included under `Redis-Test`
- Maven (`Redis-Cache-Demo`, `Redis-RateLimit-Demo`, `Redis-Lock-Demo`, and `Redis-Bloom-Filter`)
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
Redis-Cache-Demo/       Spring Boot 3 Maven: cache penetration, stampede, logical expiry
Redis-RateLimit-Demo/   Spring Boot 3 Maven: Redis Lua sliding-window API rate limit
Redis-Test/             Java Gradle sample: Jedis, Sentinel, RedisTemplate config
Redis-Lock-Demo/        Spring Boot 3 Maven: custom Redis lock and Redisson RLock
Redis-Bloom-Filter/     Java Maven Bloom filter module
Redis-Python/           Python Redis scripts and Streamlit demo
Redis_Tech.md           Redis concepts and system design notes
sentinel.conf           Example Sentinel config
```

## Run Redis-Cache-Demo

`Redis-Cache-Demo` is a Spring Boot 3 Maven project. It exposes a REST API that demonstrates three classic Redis caching problems and their solutions side-by-side.

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

Edit `Redis-Cache-Demo/src/main/resources/application.yml` to match your Redis setup:

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
cd Redis-Cache-Demo
mvn spring-boot:run
```

Or build a jar and run it:

```bash
mvn clean package -DskipTests
java -jar target/redis-cache-demo-0.0.1-SNAPSHOT.jar
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

## Run Redis-RateLimit-Demo

`Redis-RateLimit-Demo` is a Spring Boot 3 Maven demo for annotation-based API rate limiting. It uses:

- `@RateLimit` on controller methods to declare the operation key, window, and request limit.
- Spring AOP to enforce limits before the endpoint runs.
- Redis sorted sets plus a Lua script for atomic sliding-window checks.
- Batched user and IP buckets in one Redis script call.

The demo endpoint is:

```java
@RateLimit(key = "order:create", window = 1000, max = 100)
@PostMapping("/order")
public String create() {
    return "ok";
}
```

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` with no password

Start Redis locally:

```bash
redis-server --port 6379
redis-cli -p 6379 ping
```

You should see:

```text
PONG
```

### Configuration

The default configuration is in `Redis-RateLimit-Demo/src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3000ms

server:
  port: 8080
```

If port `8080` is already used by another demo, override it at startup:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"
```

### Build and test

```bash
cd Redis-RateLimit-Demo
mvn test
```

### Run

```bash
cd Redis-RateLimit-Demo
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

### Try the rate-limited endpoint

Send a request as a specific user:

```bash
curl -i -X POST http://localhost:8080/order \
  -H "X-User-Id: 42"
```

Expected successful response:

```text
HTTP/1.1 200

ok
```

The annotation allows 100 requests per user per 1000 ms. The aspect also applies a broader per-IP bucket at 4x the user limit.

Generate a quick burst:

```bash
for i in {1..110}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/order \
    -H "X-User-Id: 42"
done
```

Once the sliding window is full, responses return `429 Too Many Requests`:

```json
{
  "timestamp": "2026-04-20T19:00:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Too many requests. Please try again later."
}
```

Wait about one second and try again; old entries expire out of the sliding window.

### Inspect Redis keys

While the app is running, inspect limiter buckets:

```bash
redis-cli keys "rl:*"
redis-cli zcard "rl:user:42:order:create"
redis-cli pttl "rl:user:42:order:create"
redis-cli zcard "rl:ip:127.0.0.1:order:create"
```

When requests pass through a proxy that sets `X-Forwarded-For`, the IP bucket uses the first forwarded address.

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

## Run Redis-Lock-Demo

`Redis-Lock-Demo` contains two distributed-lock styles:

- `StockService`: custom Redis lock using `SET NX EX`, UUID lock values, `Duration` TTLs, bounded retry, watchdog renewal, and Lua compare-and-delete unlock.
- `OrderService`: Redisson `RLock` for product stock deduction, plus a custom `RedisLock` user-order flow to block duplicate concurrent submissions.

Build:

```bash
cd Redis-Lock-Demo
mvn test
```

Run the Redis-backed concurrency integration test when Redis is available:

```bash
cd Redis-Lock-Demo
mvn test -Dredis.integration=true
```

Run:

```bash
cd Redis-Lock-Demo
mvn spring-boot:run
```

The default `Redis-Lock-Demo/src/main/resources/application.yml` assumes local Redis has no password. If your Redis requires auth, pass it explicitly:

```bash
cd Redis-Lock-Demo
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.data.redis.password=123456"
```

If startup fails with `ERR AUTH <password> called without any password configured`, your app is sending a password to a no-auth Redis server. Remove any `spring.data.redis.password` override, unset `SPRING_DATA_REDIS_PASSWORD`, and run again.

Custom Redis lock stock flow:

```bash
curl -X POST 'http://localhost:8081/flash/init/1001?quantity=10'
curl -X POST 'http://localhost:8081/flash/buy/1001'
curl 'http://localhost:8081/flash/stock/1001'
```

Redisson order flow:

```bash
curl -X POST 'http://localhost:8081/orders/init/1001?quantity=10'
curl -X POST 'http://localhost:8081/orders/1001?userId=42'
```

Custom Redis lock user-order flow:

```bash
curl -X POST 'http://localhost:8081/orders/users/42'
curl 'http://localhost:8081/order/42'
```

The Redisson example stores stock in `flash:stock:{productId}`, locks stock with `lock:stock:{productId}`, and stores order records in `flash:orders:{productId}`.

### Test the custom RedisLock guarantees

The custom lock implementation in `RedisLock.java` must follow three rules:

- Acquire with `SET NX EX`: `StringRedisTemplate.setIfAbsent(key, uuid, Duration)` writes the value only when the key does not exist and sets the TTL in the same Redis operation.
- Release with Lua: `unlock(key, token)` runs a compare-and-delete script so the owner check and `DEL` happen atomically.
- Store a UUID value: `tryLockWithToken(...)` returns a UUID token, and callers must pass the same token to `unlock(...)` to avoid deleting another request's lock.

Run the app and keep Redis CLI open:

```bash
cd Redis-Lock-Demo
mvn spring-boot:run
```

In another terminal:

```bash
redis-cli
```

Check `SET NX EX` and UUID value:

```bash
curl -X POST 'http://localhost:8081/flash/init/1001?quantity=2'
curl -X POST 'http://localhost:8081/flash/buy/1001'
redis-cli get lock:flash:1001
redis-cli ttl lock:flash:1001
```

During the request, `get lock:flash:1001` should show a UUID-like value and `ttl lock:flash:1001` should be positive. If the request already finished, the key may be gone because `finally` released it.

Check Lua prevents deleting someone else's lock:

```bash
redis-cli set lock:order:test owner-a ex 30 nx
redis-cli eval "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 lock:order:test owner-b
redis-cli exists lock:order:test
redis-cli eval "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end" 1 lock:order:test owner-a
redis-cli exists lock:order:test
```

Expected result: the `owner-b` release returns `0` and the key still exists; the `owner-a` release returns `1` and the key is deleted.

Check duplicate user-order requests are blocked by the UUID lock:

```bash
curl 'http://localhost:8081/order/42' &
curl 'http://localhost:8081/order/42'
```

Expected result: one request creates the order, while the overlapping request receives a service-busy response such as `Too many requests`.

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
cd Redis-Cache-Demo && mvn test
cd ../Redis-RateLimit-Demo && mvn test
cd ../Redis-Test && ./gradlew test
cd ../Redis-Lock-Demo && mvn test
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
