# Redis Examples

This repo contains Redis notes and twelve runnable example areas:

- `Redis-Cache-Demo`: Spring Boot 3 REST service demonstrating cache penetration, cache stampede (mutex lock), and cache stampede (logical expiration) with Bloom filter, null-value sentinel, and distributed lock.
- `Redis-RateLimit-Demo`: Spring Boot 3 REST service demonstrating Redis + Lua sliding-window rate limiting with annotation-based AOP.
- `Redis-HttpSession-Demo`: Spring Boot 3 REST service demonstrating Redis-backed `HttpSession` sharing across app instances.
- `Redis-RankService-Demo`: Spring Boot 3 REST service demonstrating Redis sorted-set leaderboards — article daily rankings (view/like scoring) and a generic multi-leaderboard API with around-me queries.
- `Redis-MQ-Demo`: Spring Boot 3 REST service demonstrating Redis Streams consumer groups and sorted-set delayed queues for order workflows.
- `Redis-BitMap-Demo`: Spring Boot 3 REST service demonstrating Redis bitmap daily sign-in, monthly counts, and current streaks.
- `Redis-Geo-Demo`: Spring Boot 3 REST service demonstrating Redis GEO nearby-shop search with coordinates and distances.
- `Redis-Lock-Demo`: Spring Boot 3 REST service demonstrating custom Redis locks and Redisson `RLock`.
- `Redis-Autocomplete-Demo`: Spring Boot 3 REST service demonstrating Redis Stack RediSearch suggestion dictionaries, ranked prefix suggestions, and fuzzy autocomplete.
- `Redis-Bloom-Filter`: Java/Maven Bloom filter implementation backed by Redis Cluster bitmaps.
- `Redis-Test`: Java/Gradle examples for Jedis direct connections, connection pools, Sentinel, and a Spring `RedisTemplate` configuration.
- `Redis-Python`: Python examples for Redis direct/pool/Sentinel connections, Pub/Sub, RediSearch suggestions, and a small Streamlit demo.

Older Redis command notes are kept in `README_bk.md`, and the deeper study notes are in `Redis_Tech.md`.

## Prerequisites

- Java 17 or newer (`Redis-Cache-Demo` requires 17; `Redis-Test` works on 11+)
- Gradle wrapper, already included under `Redis-Test`
- Maven (`Redis-Cache-Demo`, `Redis-RateLimit-Demo`, `Redis-HttpSession-Demo`, `Redis-Lock-Demo`, and `Redis-Bloom-Filter`)
- Python 3.9+
- Redis server or Redis Stack, depending on the example

Install Redis on macOS:

```bash
brew install redis
redis-server --port 6379
redis-cli -p 6379 ping
```

Note: `Redis-Autocomplete-Demo` requires RediSearch support, so plain Homebrew `redis-server` is not sufficient unless you also load the RediSearch module or use a Redis Stack build.

If you run the Java sample exactly as configured, Redis must require password `123456`. Otherwise edit:

```text
Redis-Test/src/main/resources/application.yml
```

For a local Redis instance without auth, remove or blank the `password` value.

## Project Layout

```text
Redis-Cache-Demo/          Spring Boot 3 Maven: cache penetration, stampede, logical expiry
Redis-RateLimit-Demo/      Spring Boot 3 Maven: Redis Lua sliding-window API rate limit
Redis-HttpSession-Demo/    Spring Boot 3 Maven: Redis-backed HttpSession sharing
Redis-RankService-Demo/    Spring Boot 3 Maven: Redis sorted-set leaderboards and article metrics
Redis-MQ-Demo/             Spring Boot 3 Maven: Redis Streams and ZSET delayed queue
Redis-BitMap-Demo/         Spring Boot 3 Maven: Redis bitmap daily sign-in
Redis-Geo-Demo/            Spring Boot 3 Maven: Redis GEO nearby-shop search
Redis-Lock-Demo/           Spring Boot 3 Maven: custom Redis lock and Redisson RLock
Redis-Autocomplete-Demo/   Spring Boot 3 Maven: Redis Stack RediSearch autocomplete suggestions
Redis-Bloom-Filter/        Java Maven Bloom filter module
Redis-Test/                Java Gradle sample: Jedis, Sentinel, RedisTemplate config
Redis-Python/              Python Redis scripts and Streamlit demo
Redis_Tech.md              Redis concepts and system design notes
sentinel.conf              Example Sentinel config
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

If startup fails with `Port 8080 was already in use`, either stop the process using that port or start this demo on another port:

```bash
# See what is using 8080
lsof -nP -iTCP:8080 -sTCP:LISTEN

# Run this demo on 8082 instead
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"
```

When using a different port, replace `8080` with that port in the curl examples below.

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

## Run Redis-HttpSession-Demo

`Redis-HttpSession-Demo` is a Spring Boot 3 Maven demo for sharing servlet `HttpSession` state through Redis. It uses Spring Session Data Redis so multiple app instances can read the same login session when the client sends the same `SESSION` cookie.

### What it demonstrates

| Endpoint | Description |
|---|---|
| `GET /` | Creates or reads the current session and returns the current server node |
| `POST /auth/login` | Stores `loginUser`, `loginTime`, and `loginTraceId` in the Redis-backed session |
| `GET /auth/me` | Reads the current session attributes |
| `POST /auth/logout` | Invalidates the current session and removes it from Redis |

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` with no password

```bash
redis-server --port 6379
redis-cli -p 6379 ping
```

### Configuration

The default configuration is in `Redis-HttpSession-Demo/src/main/resources/application.yml`:

```yaml
spring:
  session:
    store-type: redis
    timeout: 30m
    redis:
      namespace: spring:session:demo
```

The app defaults to port `8080`. If another demo is already using it, pass a different port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8083"
```

### Build and test

```bash
cd Redis-HttpSession-Demo
mvn test
```

### Run one instance

```bash
cd Redis-HttpSession-Demo
mvn spring-boot:run
```

Login and save the session cookie:

```bash
curl -i -c /tmp/redis-session.cookies \
  -H "Content-Type: application/json" \
  -d '{"username":"alice"}' \
  http://localhost:8080/auth/login
```

Read the session with the saved cookie:

```bash
curl -b /tmp/redis-session.cookies http://localhost:8080/auth/me
```

### Run two instances

**Step 1 — Build the project:**

```bash
cd Redis-HttpSession-Demo
mvn clean package -DskipTests
```

**Step 2 — Start node-a on port 8080 (Terminal 1):**

```bash
cd Redis-HttpSession-Demo
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080 -Dserver.node=node-a"
```

**Step 3 — Start node-b on port 8081 (Terminal 2):**

```bash
cd Redis-HttpSession-Demo
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8081 -Dserver.node=node-b"
```

**Step 4 — Login on node-a and save the session cookie:**

```bash
curl -c /tmp/cookies.txt -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice"}'
```

Note the `sessionId` in the response.

**Step 5 — Read the session on node-b using the same cookie:**

```bash
curl -b /tmp/cookies.txt http://localhost:8081/auth/me
```

The response should show `"username":"alice"` while `serverNode` is `node-b`, proving the session was read from Redis rather than local process memory.

**Step 6 — Logout on node-b:**

```bash
curl -b /tmp/cookies.txt -X POST http://localhost:8081/auth/logout
```

**Step 7 — Confirm the session is gone on node-a:**

```bash
curl -b /tmp/cookies.txt http://localhost:8080/auth/me
# "authenticated": false — session was invalidated in Redis
```

**Inspect Redis session keys at any point:**

```bash
redis-cli keys "spring:session:demo:*"
redis-cli hgetall "spring:session:demo:sessions:<sessionId>"
```

## Run Redis-RankService-Demo

`Redis-RankService-Demo` is a Spring Boot 3 Maven demo for Redis sorted-set leaderboards. It has two independent use cases:

- **Article metrics** — records article views and likes, scores each article in a daily sorted set, and exposes a top-N leaderboard.
- **Generic leaderboard** — a parameterised API where any leaderboard name maps to its own Redis key. Supports set score, increment, top-N, rank lookup, around-me neighbourhood, remove, and count.

### What it demonstrates

| Redis command | Used for |
|---|---|
| `ZADD` | Set a member's score directly |
| `ZINCRBY` | Atomically increment a score (view/like events) |
| `ZREVRANGEWITHSCORES` | Top-N leaderboard with scores |
| `ZREVRANK` | A member's rank from the top (0-indexed internally) |
| `ZSCORE` | A single member's current score |
| `ZREVRANK` + `ZREVRANGEWITHSCORES` | Around-me window: N positions above and below |
| `ZREM` | Remove a member from the leaderboard |
| `ZCARD` | Total member count |
| `INCR` | View and like counters |
| `SADD` + `EXPIRE` | Daily unique-visitor (UV) tracking per article |

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` with no password

```bash
redis-server --port 6379
redis-cli -p 6379 ping
```

### Build and test

```bash
cd Redis-RankService-Demo
mvn test
```

### Run

```bash
cd Redis-RankService-Demo
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

### Article metrics use case

Record a read event (increments view, PV, UV counters and adds 1 point to today's article rank):

```bash
curl -X POST http://localhost:8080/articles/read \
  -H "Content-Type: application/json" \
  -d '{"articleId":1,"userId":"u42","visitorId":"v99"}'
```

Record a like (increments like counter and adds 5 points to today's article rank):

```bash
curl -X POST http://localhost:8080/articles/like \
  -H "Content-Type: application/json" \
  -d '{"articleId":1,"userId":"u42"}'
```

Get article stats (views, likes, PV, UV, and today's leaderboard rank):

```bash
curl http://localhost:8080/articles/1/stats
```

Get today's top-10 articles:

```bash
curl http://localhost:8080/rank/top?n=10
```

Get the rank and score for a specific article:

```bash
curl http://localhost:8080/rank/me/article:1
```

### Generic leaderboard use case

All endpoints are scoped to a leaderboard name in the path (e.g. `game:weekly`, `game:alltime`).

Set a player's score directly (ZADD):

```bash
curl -X POST http://localhost:8080/leaderboard/game:weekly/score \
  -H "Content-Type: application/json" \
  -d '{"memberId":"alice","score":1500}'
```

Increment a player's score (ZINCRBY — use for event-driven scoring):

```bash
curl -X POST http://localhost:8080/leaderboard/game:weekly/increment \
  -H "Content-Type: application/json" \
  -d '{"memberId":"alice","score":50}'
```

Get the top-5 players:

```bash
curl "http://localhost:8080/leaderboard/game:weekly/top?n=5"
```

Get a player's rank and score:

```bash
curl http://localhost:8080/leaderboard/game:weekly/me/alice
```

Get the 3 players above and below a given player (around-me):

```bash
curl "http://localhost:8080/leaderboard/game:weekly/around/alice?radius=3"
```

Remove a player from the leaderboard:

```bash
curl -X DELETE http://localhost:8080/leaderboard/game:weekly/player/alice
```

Get total member count (ZCARD):

```bash
curl http://localhost:8080/leaderboard/game:weekly/count
```

### Inspect Redis keys

```bash
# Article daily leaderboard key (changes each day)
redis-cli keys "rank:article:daily:*"
redis-cli zrevrangebyscore "rank:article:daily:$(date +%Y-%m-%d)" +inf -inf WITHSCORES LIMIT 0 10

# Generic leaderboard key (whatever name you used)
redis-cli zrevrangebyscore game:weekly +inf -inf WITHSCORES LIMIT 0 10
redis-cli zcard game:weekly

# Article counters
redis-cli get article:view:1
redis-cli get article:like:1
redis-cli get article:pv:1
redis-cli scard "article:uv:1:$(date +%Y-%m-%d)"
```

## Run Redis-MQ-Demo

`Redis-MQ-Demo` is a Spring Boot 3 Maven demo for Redis-backed order messaging. It has two independent use cases:

- **Order event stream** — writes order IDs to a Redis Stream (capped at 1 000 entries with `XTRIM MAXLEN ~`), consumes them through a consumer group, batches acknowledgements in a single `XACK` call, and keeps a recent processed-order audit list.
- **Delayed order close** — schedules unpaid orders in a sorted set with the trigger timestamp as score, claims due entries with an atomic remove, and keeps a recent closed-order audit list.

### What it demonstrates

| Redis feature | Used for |
|---|---|
| `XADD` | Append order events to `stream:order` |
| `XTRIM MAXLEN ~` | Cap `stream:order` at 1 000 entries after each write |
| `XGROUP CREATE` | Bootstrap consumer group on first startup |
| `XREADGROUP` | Reliable asynchronous order consumption |
| `XACK` (batched) | Acknowledge a batch of processed stream records in one call |
| `XPENDING` | Observe unacknowledged messages |
| `XDEL` | Remove the transient bootstrap entry after group creation |
| `ZADD` | Schedule delayed order-close jobs with score = trigger epoch ms |
| `ZRANGEBYSCORE` + `ZREM` | Claim due delayed jobs; ZREM acts as atomic ownership check |
| `LPUSH` + `LTRIM` (pipelined) | Append and cap recent processed/closed audit lists in one round trip |

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` with no password

```bash
redis-server --port 6379
redis-cli -p 6379 ping
```

### Configuration

The default configuration is in `Redis-MQ-Demo/src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      connect-timeout: 2s
      timeout: 3s
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: 2s
```

Lettuce connection pooling requires `commons-pool2` on the classpath, which is already declared in `pom.xml`.

If port `8080` is already in use, override it at startup:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8084"
```

### Build and test

```bash
cd Redis-MQ-Demo
mvn test
```

### Run

```bash
cd Redis-MQ-Demo
mvn spring-boot:run
```

The server starts on `http://localhost:8080`. On startup the app creates consumer group `order-group` on `stream:order` (MKSTREAM — no pre-existing stream required).

### Order event stream use case

**Step 1 — Publish an order event:**

```bash
curl -X POST http://localhost:8080/mq/stream/order \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-1001"}'
```

Expected response:

```json
{
  "message": "stream message sent",
  "stream": "stream:order",
  "messageId": "1713600000000-0",
  "orderId": "order-1001"
}
```

**Step 2 — Publish a few more events:**

```bash
curl -s -X POST http://localhost:8080/mq/stream/order \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-1002"}' | jq .

curl -s -X POST http://localhost:8080/mq/stream/order \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-1003"}' | jq .
```

**Step 3 — Check stream size and consumer group metadata:**

```bash
curl http://localhost:8080/mq/stream/info
```

Expected response:

```json
{
  "stream": "stream:order",
  "size": 3,
  "group": "order-group",
  "consumer": "consumer-a"
}
```

**Step 4 — Wait for the consumer to run (scheduled every 2 s), then check processed orders:**

```bash
curl "http://localhost:8080/mq/stream/processed?n=10"
```

Expected response (most recent first):

```json
["order-1003", "order-1002", "order-1001"]
```

The `n` parameter is capped at 100.

### Delayed order close use case

**Step 1 — Schedule an order to close after 5 seconds:**

```bash
curl -X POST http://localhost:8080/mq/delay/order \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-2001","delayMs":5000}'
```

Expected response:

```json
{
  "message": "delay job scheduled",
  "queue": "delay:order:close",
  "orderId": "order-2001",
  "delayMs": 5000
}
```

**Step 2 — Schedule a second order with the default 30-minute delay:**

```bash
curl -X POST http://localhost:8080/mq/delay/order \
  -H "Content-Type: application/json" \
  -d '{"orderId":"order-2002","delayMs":1800000}'
```

**Step 3 — Peek at pending jobs (sorted by trigger time, earliest first):**

```bash
curl "http://localhost:8080/mq/delay/peek?n=10"
```

Expected response:

```json
["order-2001", "order-2002"]
```

**Step 4 — Wait 5 seconds for the scanner to claim `order-2001`, then check closed orders:**

```bash
curl "http://localhost:8080/mq/delay/closed?n=10"
```

Expected response:

```json
["order-2001"]
```

`order-2002` remains in the sorted set until its trigger time is reached.

### Full endpoint reference

| Method | URL | Description |
|---|---|---|
| `POST` | `/mq/stream/order` | Publish an order event to the Redis Stream |
| `GET` | `/mq/stream/info` | Stream size and consumer group metadata |
| `GET` | `/mq/stream/processed?n=N` | Last N processed order IDs (max 100) |
| `POST` | `/mq/delay/order` | Schedule an order-close job with `delayMs` |
| `GET` | `/mq/delay/peek?n=N` | Top N pending delayed jobs by trigger time (max 100) |
| `GET` | `/mq/delay/closed?n=N` | Last N closed order IDs (max 100) |

### Inspect Redis keys

```bash
# Stream internals
redis-cli xlen stream:order
redis-cli xinfo stream stream:order
redis-cli xinfo groups stream:order
redis-cli xrange stream:order - +

# Confirm stream is capped — length should stay at or below 1000 under load
redis-cli xlen stream:order

# Processed audit list
redis-cli lrange stream:order:processed 0 9

# Delay queue (score = epoch ms trigger time)
redis-cli zrange delay:order:close 0 -1 withscores

# Closed order audit list
redis-cli lrange delay:order:closed 0 9
```

Confirm a claimed order is removed from the sorted set after closure:

```bash
redis-cli zscore delay:order:close order-2001
# (nil) — claimed and removed by the scanner
```

## Run Redis-BitMap-Demo

`Redis-BitMap-Demo` is a Spring Boot 3 Maven demo for daily user sign-in tracking with Redis bitmaps. Each user/month maps to one bitmap key, where bit offset `0` is day 1, offset `1` is day 2, and so on.

### What it demonstrates

| Redis command | Used for |
|---|---|
| `SETBIT` | Mark a user as signed in for a date |
| `GETBIT` | Check whether a user signed in on a date |
| `BITCOUNT` | Count signed-in days for a month |
| `BITFIELD` | Read month-to-date bits and calculate the current streak |

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` with no password

```bash
redis-server --port 6379
redis-cli -p 6379 ping
```

### Build and test

```bash
cd Redis-BitMap-Demo
mvn test
```

### Run

```bash
cd Redis-BitMap-Demo
mvn spring-boot:run
```

The server starts on `http://localhost:8080`. If another demo is using port `8080`, run this one on a different port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8085"
```

### Daily sign-in use case

Sign in for today:

```bash
curl -X POST http://localhost:8080/sign/42
```

Sign in for a specific date, useful for seeding demo data:

```bash
curl -X POST "http://localhost:8080/sign/42?date=2026-04-20"
```

Get signed-in days for that month:

```bash
curl "http://localhost:8080/sign/42/days?date=2026-04-20"
```

Get the current streak as of a date:

```bash
curl "http://localhost:8080/sign/42/streak?date=2026-04-20"
```

Get the full summary:

```bash
curl "http://localhost:8080/sign/42/summary?date=2026-04-20"
```

### Inspect Redis keys

```bash
redis-cli getbit sign:42:202604 19
redis-cli bitcount sign:42:202604
redis-cli bitfield sign:42:202604 get u20 0
```

## Run Redis-Geo-Demo

`Redis-Geo-Demo` is a Spring Boot 3 Maven demo for nearby-shop lookup with Redis GEO commands. It stores shop coordinates in one GEO set and returns nearby shops sorted by distance.

### What it demonstrates

| Redis command | Used for |
|---|---|
| `GEOADD` | Store shop longitude/latitude by shop ID |
| `GEORADIUS` | Query shops within a radius from a coordinate |
| `WITHDIST` | Return distance from the query coordinate |
| `WITHCOORD` | Return stored longitude/latitude for each shop |
| `GEOPOS` / `GEODIST` | Inspect stored positions and distances in Redis CLI |

### Prerequisites

- Java 17+
- Maven 3.6+
- Redis running on `localhost:6379` with no password

```bash
redis-server --port 6379
redis-cli -p 6379 ping
```

### Configuration

The default configuration is in `Redis-Geo-Demo/src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3s

server:
  port: 8080
```

### Build and test

```bash
cd Redis-Geo-Demo
mvn test
```

### Run

```bash
cd Redis-Geo-Demo
mvn spring-boot:run
```

The server starts on `http://localhost:8080`. If another demo is using port `8080`, run this one on a different port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8086"
```

### Nearby shop use case

**Step 1 — Add a few shops near Manhattan:**

```bash
curl -X POST http://localhost:8080/geo/shops \
  -H "Content-Type: application/json" \
  -d '{"shopId":"empire-coffee","lng":-73.9857,"lat":40.7484}'

curl -X POST http://localhost:8080/geo/shops \
  -H "Content-Type: application/json" \
  -d '{"shopId":"bryant-snacks","lng":-73.9832,"lat":40.7536}'

curl -X POST http://localhost:8080/geo/shops \
  -H "Content-Type: application/json" \
  -d '{"shopId":"chelsea-books","lng":-74.0018,"lat":40.7411}'
```

Each successful add returns a confirmation with the stored coordinates:

```json
{
  "message": "shop added",
  "key": "geo:shop",
  "shopId": "empire-coffee",
  "lng": -73.9857,
  "lat": 40.7484
}
```

**Step 2 — Find the nearest shops within 2 km:**

```bash
curl "http://localhost:8080/geo/nearby?lng=-73.9851&lat=40.7580&radiusKm=2&limit=5"
```

The response echoes the query parameters and returns shops sorted by ascending distance:

```json
{
  "queryLng": -73.9851,
  "queryLat": 40.758,
  "radiusKm": 2.0,
  "limit": 5,
  "shops": [
    {
      "shopId": "bryant-snacks",
      "distanceKm": 0.51,
      "lng": -73.9832,
      "lat": 40.7536
    },
    {
      "shopId": "empire-coffee",
      "distanceKm": 0.88,
      "lng": -73.9857,
      "lat": 40.7484
    }
  ]
}
```

**Step 3 — Try with a larger radius to include all three shops:**

```bash
curl "http://localhost:8080/geo/nearby?lng=-73.9851&lat=40.7580&radiusKm=5&limit=10"
```

### Validation rules

Query parameters are validated at the API boundary before the Redis call is made:

| Parameter | Constraint | Default |
|---|---|---|
| `lng` | `-180.0` to `180.0` | required |
| `lat` | `-90.0` to `90.0` | required |
| `radiusKm` | `0.001` to `1000.0` | `5` |
| `limit` | `1` to `100` | `10` |

A request outside these ranges returns `400 Bad Request` with a validation error before touching Redis.

### Full endpoint reference

| Method | URL | Description |
|---|---|---|
| `POST` | `/geo/shops` | Add a shop with its coordinates to the GEO set |
| `GET` | `/geo/nearby` | Return nearby shops sorted by distance |

### Inspect Redis keys

```bash
# List all members with their internal geohash scores
redis-cli zrange geo:shop 0 -1 withscores

# Look up stored coordinates for specific shops
redis-cli geopos geo:shop empire-coffee bryant-snacks chelsea-books

# Distance between two shops
redis-cli geodist geo:shop empire-coffee bryant-snacks km

# Raw radius query matching what the API runs
redis-cli georadius geo:shop -73.9851 40.7580 2 km withdist withcoord asc count 5
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
cd ../Redis-HttpSession-Demo && mvn test
cd ../Redis-RankService-Demo && mvn test
cd ../Redis-MQ-Demo && mvn test
cd ../Redis-BitMap-Demo && mvn test
cd ../Redis-Geo-Demo && mvn test
cd ../Redis-Lock-Demo && mvn test
cd ../Redis-Bloom-Filter && mvn test
cd ../Redis-Test && ./gradlew test
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
