# Redis Autocomplete Demo

`Redis-Autocomplete-Demo` is a Spring Boot 3 REST service demonstrating Redis Stack + RediSearch autocomplete dictionaries. It loads weighted keywords into a suggestion dictionary with `FT.SUGADD` and queries ranked prefix suggestions with `FT.SUGGET`, including fuzzy matching.

## What it demonstrates

| Capability | Redis command | Endpoint |
|---|---|---|
| Import weighted sample data | `FT.SUGADD` | `POST /autocomplete/import/sample` |
| Add one suggestion | `FT.SUGADD` | `POST /autocomplete/suggestions` |
| Prefix suggestions | `FT.SUGGET` | `GET /autocomplete/suggest` |
| Exact vs fuzzy comparison | `FT.SUGGET`, `FT.SUGGET FUZZY` | `GET /autocomplete/suggest/compare` |
| Dictionary size and reset | `FT.SUGLEN`, `DEL` | `GET /autocomplete/dictionary`, `DELETE /autocomplete/dictionary` |

## Prerequisites

- Java 17+
- Maven 3.6+
- Redis with RediSearch support running on `localhost:6379`

Important:

- Plain `redis-server --port 6379` from the Homebrew `redis` formula is not enough for this demo
- This demo requires RediSearch commands: `FT.SUGADD`, `FT.SUGGET`, and `FT.SUGLEN`
- If `redis-cli COMMAND INFO FT.SUGADD` returns `(nil)`, your Redis instance does not support this demo yet

Start Redis Stack:

```bash
docker run --rm -p 6379:6379 redis/redis-stack:latest
```

If you prefer a local non-Docker setup, you still need a RediSearch-enabled server such as `redis-stack-server` or a Redis server started with the RediSearch module loaded.

Quick verification:

```bash
redis-cli -p 6379 COMMAND INFO FT.SUGADD
redis-cli -p 6379 COMMAND INFO FT.SUGGET
```

Both commands should return command metadata. If they return `(nil)`, the demo endpoints will fail with `500` because the Redis server does not recognize `FT.*` commands.

## Configuration

Edit `Redis-Autocomplete-Demo/src/main/resources/application.yml` to match your Redis setup:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

If you run a RediSearch-enabled Redis instance on another port such as `6380`, update `port` accordingly.

## Build and run

```bash
cd Redis-Autocomplete-Demo
mvn spring-boot:run
```

Or build a jar and run it:

```bash
mvn clean package
java -jar target/redis-autocomplete-demo-0.0.1-SNAPSHOT.jar
```

The server starts on `http://localhost:8080`.

If startup succeeds but requests return `500`, check the Redis command support again:

```bash
redis-cli -p 6379 COMMAND INFO FT.SUGADD
```

That is the fastest way to tell whether the issue is missing RediSearch support versus a Spring application problem.

## Sample dataset

The bundled dataset lives in `src/main/resources/keywords_counts.txt`. Each line ends with a numeric score and everything before it becomes the suggestion text.

Examples:

```text
strawberry jam 870
python redis 740
redis autocomplete 710
```

The sample terms intentionally mix food, Java, Python, Redis, search, and AI topics so ranking and fuzzy lookups are easy to see.

## Demo flow

### 1. Import the sample dictionary

```bash
curl -X POST "http://localhost:8080/autocomplete/import/sample?clear=true"
```

Expected result:

- loads the bundled `keywords_counts.txt`
- clears `keywords_ac` first for repeatable demos
- returns inserted row count and current dictionary size

### 2. Query ranked suggestions

```bash
curl "http://localhost:8080/autocomplete/suggest?prefix=redis&maxResults=5&fuzzy=true"
```

Expected behavior:

- `redis` returns terms like `redis`, `redis stack`, `redis search`, and `redis autocomplete`
- results are ordered by score descending

### 3. Compare exact and fuzzy matching

```bash
curl "http://localhost:8080/autocomplete/suggest/compare?prefix=pythn&maxResults=5"
```

Expected behavior:

- exact results may be sparse for typo prefixes
- fuzzy results can still return `python` and `pytorch`

### 4. Add a custom suggestion

```bash
curl -X POST "http://localhost:8080/autocomplete/suggestions" \
  -H "Content-Type: application/json" \
  -d '{"term":"redis vector search","score":780,"incremental":false}'
```

### 5. Inspect or clear the dictionary

```bash
curl "http://localhost:8080/autocomplete/dictionary"
curl -X DELETE "http://localhost:8080/autocomplete/dictionary"
```

## Full endpoint reference

| Method | URL | Description |
|---|---|---|
| `POST` | `/autocomplete/import/sample?clear=true&incremental=false` | Import bundled sample keywords |
| `POST` | `/autocomplete/suggestions` | Add one weighted suggestion |
| `GET` | `/autocomplete/suggest?prefix=redis&maxResults=5&fuzzy=true` | Query suggestions |
| `GET` | `/autocomplete/suggest/compare?prefix=pythn&maxResults=5` | Compare exact and fuzzy matches |
| `GET` | `/autocomplete/dictionary` | Get current dictionary size |
| `DELETE` | `/autocomplete/dictionary` | Clear the dictionary key |

## Inspect Redis while running

```bash
redis-cli FT.SUGLEN keywords_ac
redis-cli FT.SUGGET keywords_ac redis MAX 5 WITHSCORES
redis-cli FT.SUGGET keywords_ac pythn FUZZY MAX 5 WITHSCORES
redis-cli DEL keywords_ac
```
