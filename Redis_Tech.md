## Deep Dive on Redis

Redis is a self-described ["data structure store"](https://redis.io/docs/latest/) written in C. It's in-memory 🫢 and single threaded 😱 making it very fast and easy to reason about.

One important reason you might not want to use Redis is because you need **durability**. While there are some reasonable strategies for (using Redis' Append-Only File [AOF](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)) to *minimize* data loss, you don't get the same guarantees you might get from e.g. a relational database about commits being written to disk. This is an intentional tradeoff made by the Redis team in favor of speed, but alternative implementations (e.g. [AWS' MemoryDB](https://aws.amazon.com/memorydb/)) will compromise a bit on speed to give you disk-based durability. If you need it, it's there!

Some of the most fundamental data structures supported by Redis:

- Strings
- Hashes (objects/dictionaries)
- Lists
- Sets
- Sorted Sets (Priority Queues)
- Bloom Filters (probabilistic set membership; allows false positives)
- Geospatial Indexes
- Time Series

### Redis for Bitmap Statistics

Redis bitmaps are compact counters for boolean user facts. If user IDs are numeric, you can use the user ID as the bit offset:

```
SETBIT activity:online:20260520 42 1
GETBIT activity:online:20260520 42
BITCOUNT activity:online:20260520
```

This is useful for online-user statistics, daily active users, sign-in calendars, and retention analysis.

For multi-day activity, store one bitmap per day and use bit operations:

```
BITOP AND activity:result:7day:all activity:online:20260520 ... activity:online:20260526
BITCOUNT activity:result:7day:all
```

The `AND` result counts users who were online every day in the range. `OR` counts users who were online on at least one day. Retention from day A to day B is also an `AND` between those two daily bitmaps.

Tradeoffs: bitmap offsets are efficient when user IDs are reasonably dense. Very large sparse user IDs can create large strings; in that case, map external IDs to compact internal offsets first.

### Redis as a Cache

The most common deployment scenario of Redis is as a cache. In this case, the root keys and values of Redis map to the keys and values in our cache. Redis can distribute this hash map trivially across all the nodes of our cluster enabling us to scale without much fuss - if we need more capacity we simply add nodes to the cluster.

When using Redis as a cache, you'll often employ a time to live (TTL) on each key. Redis guarantees you'll never read the value of a key after the TTL has expired and the TTL is used to decide which items to evict from the server - keeping the cache size manageable even in the case where you're trying to cache more items than memory allows.

Using Redis in this fashion doesn't solve one of the more important problems caches face: the "hot key" problem, though Redis is not unique in this respect vs alternatives like Memcached or other highly scaled databases like DynamoDB.

### Redis for Shared Session State

Redis is also a practical shared data layer for short-lived distributed application state. A common example is distributed Session in a horizontally scaled web application: requests for the same user may land on different app instances, but each instance can read and update the same session data in Redis.

In a Spring Boot servlet application, `spring-session-data-redis` replaces the default in-memory `HttpSession` store with Redis:

```xml
<dependency>
  <groupId>org.springframework.session</groupId>
  <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

The app then enables Redis-backed sessions and sets a TTL:

```java
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
```

Typical data stored here includes login user IDs, auth metadata, verification-code state, and small shopping-cart drafts. Keep this data small and expiration-bound. Redis-backed session state improves horizontal scaling and failover compared with local JVM sessions, but it should not become the source of truth for durable business data.

### Redis for Global ID Segments

Redis counters are a simple way to allocate globally unique numeric IDs. For sharded database/table systems, an application often needs an ID before it can decide which shard or table to write to.

Calling Redis once per ID works, but it adds a network round trip to every insert:

```
INCR global:id:user
```

A higher-throughput pattern is segment allocation. Each app instance asks Redis for a block of IDs with `INCRBY`, then serves IDs from memory until the local block is exhausted:

```
INCRBY global:id:user 1000 # returns 1000, app owns 1..1000
INCRBY global:id:user 1000 # returns 2000, app owns 1001..2000
```

The returned value is the segment end. The segment start is `end - step + 1`. For example, if `INCRBY global:id:user 1000` returns `5000`, the caller owns IDs `4001..5000`.

This is a good fit for:

- user IDs, order IDs, payment IDs, or other numeric primary keys
- sharded table routing such as `user_${userId % 16}`
- reducing Redis QPS by reserving `1000` or `10000` IDs at a time

Tradeoffs: if an app crashes before using the whole segment, some IDs are skipped. That is usually acceptable for unique IDs, but not acceptable when IDs must be gapless. Redis persistence and replication still matter because losing the counter can cause duplicate ranges after failover.

### Redis for Delayed Counter Sync

Redis `INCR` is a natural fit for high-frequency counters such as article views, likes, downloads, shares, or profile visits. For these counters, the user-facing number usually needs to update quickly, while the database can lag by a few seconds or minutes.

A common pattern is:

```
INCR article:view:1001
SADD article:dirty:view 1001
```

The request path only writes Redis. A background job periodically drains dirty IDs, reads the latest Redis values, and writes snapshots to the database:

```
SPOP article:dirty:view
GET article:view:1001
UPDATE article_counter SET views = <redis-value> WHERE article_id = 1001
```

This reduces database write pressure because many increments collapse into one database update. It is a good fit when counters tolerate eventual consistency.

Important tradeoffs:

- Redis should enable persistence or replication if losing recent increments is unacceptable.
- Database values are stale between sync runs, so reads that need the freshest count should read Redis.
- Sync by snapshot (`SET db_count = redis_count`) is idempotent and easy to retry. Sync by delta needs more careful atomic drain logic.
- Hot counters can still become hot keys; batching DB writes does not remove Redis-side hot key pressure.

### Redis as a Distributed Lock

Another common use of Redis in system design settings is as a distributed lock. Occasionally we have data in our system and we need to maintain consistency during updates (e.g. the very common Design Ticketmaster system design question), or we need to make sure multiple people aren't performing an action at the same time (e.g. Design Uber).

A very simple distributed lock with a timeout might use the atomic increment (INCR) with a TTL. This is basically a shared counter. When we want to try to acquire the lock, we run INCR. If the response is 1 (i.e. we were the first person to try to grab the lock, so we own it!), we proceed. If the response is > 1 (i.e. someone else beat us and has the lock), we wait and retry again later. When we're done with the lock, we can DEL the key so that other processes can make use of it.

For production-style Redis locks, do not split acquisition into `SETNX` and `EXPIRE`, and do not release with a blind `DEL`:

```java
Long flag = jedis.setnx(key, "1");
if (flag == 1) {
    jedis.expire(key, 10);
}
jedis.del(key);
```

This can leave a permanent lock if the owner crashes between `SETNX` and `EXPIRE`. It can also delete someone else's lock: request A acquires the lock, pauses until the lock expires, request B acquires the same key, and then request A finally calls `DEL`.

A safer single-instance pattern is:

- acquire with one atomic Redis command: `SET lock:key <uuid-token> NX EX <ttl>`
- store a unique owner token, not a constant value like `"1"`
- release with Lua compare-and-delete: delete only when `GET lock:key` still equals the owner token
- renew with Lua compare-and-expire if the critical section can exceed the initial TTL

More sophisticated locks in Redis can use the Redlock algorithm together with fencing tokens if you want an airtight solution.

### Redis for Leaderboards

Redis' sorted sets maintain ordered data which can be queried in log time which make them appropriate for leaderboard applications. The high write throughput and low read latency make this especially useful for scaled applications where something like a SQL DB will start to struggle.

In Post Search we have a need to find the posts which contain a given keyword (e.g. "tiger") which have the most likes (e.g. "Tiger Woods made an appearance..." @ 500 likes).

We can use Redis' sorted sets to maintain a list of the top liked posts for a given keyword. 
Periodically, we can remove low-ranked posts to save space.

```
ZADD tiger_posts 500 "SomeId1" # Add the Tiger woods post
ZADD tiger_posts 1 "SomeId2" # Add some tweet about zoo tigers
ZREMRANGEBYRANK tiger_posts 0 -6 # Remove all but the top 5 posts
```

### Redis for Rate Limiting

As a data structure server, implementing a wide variety of rate limiting algorithms is possible. A common algorithm is a fixed-window rate limiter where we guarantee that the number of requests does not exceed N over some fixed window of time W.

Implementation of this in Redis is simple. When a request comes in, we increment (INCR) the key for our rate limiter and check the response. If the response is greater than N, we wait. If it's less than N, we can proceed. We call EXPIRE on our key so that after time period W, the value is reset.

```
INCR expensive_service_rate_limit # 5
EXPIRE expensive_service_rate_limit 60 LT
```

For a visitor-scoped limiter, build the key from stable request dimensions such as IP, user ID, and operation:

```
INCR rl:counter:203.0.113.10:user42:order:create
EXPIRE rl:counter:203.0.113.10:user42:order:create 60
```

The first request in the window sets the TTL. If the incremented value is greater than the max allowed count, return false or reject the request. This fixed-window counter is cheap and easy to understand, but it can allow boundary bursts: a client may use the full quota at the end of one window and again at the start of the next.

If you need a sliding window, you can store timestamps in a Sorted Set per key and remove old entries before counting; run the check in Lua to keep it atomic.

### Redis for Proximity Search

Redis natively supports geospatial indexes with commands like GEOADD and GEOSEARCH. The basic commands are simple:

```
GEOADD key longitude latitude member # Adds "member" to the index at key "key"
GEOSEARCH key FROMLONLAT longitude latitude BYRADIUS radius unit # Searches the index at key "key" at specified position and radius
```

The search command, in this instance, runs in O(N+log(M)) time where N is the number of elements in the radius and M is the number of items inside the shape.

Why do we have both N and M? Redis' geospatial commands use geohashes under the hood to index the data. These geohashes allow us to grab candidates in grid-aligned bounding boxes. But these boxes are square and imprecise. A second pass takes the candidates and filters them to only include items that are within the exact radius.

### Redis for Event Sourcing

Redis' streams are append-only logs similar to Kafka's topics. The basic idea behind Redis streams is that we want to durably add items to a log and then have a distributed mechanism for consuming items from these logs. Redis solves this problem with streams (managed with commands like XADD) and consumer groups (commands like XREADGROUP and XCLAIM).

A simple example is a work queue. We want to add items to the queue and have them processed. At any point in time one of our workers might fail, and in these instances we'd like to re-process them once the failure is detected. With Redis streams we add items onto the queue with commands like XADD and have a single consumer group attached to the stream for our workers. This consumer group is maintaining a reference to the items processed via the stream and, in the case a worker fails, provides a way for a new worker to claim (XCLAIM) and restart processing that message.

### Redis for Pub/Sub

Redis natively supports a publish/subscribe (Pub/Sub) messaging pattern, allowing messages to be broadcast to multiple subscribers in real time. This is useful for building chat systems, real-time notifications, or any scenario where you want to decouple message producers from consumers (more discussion on this in our Realtime Updates pattern).

When a client subscribes to a channel, it will receive any messages published to that channel as long as the connection remains open. This makes Pub/Sub great for ephemeral, real-time communication, but it's important to note that messages are not persisted—if a subscriber is offline when a message is published, it will miss that message entirely.

Pub/Sub clients use a single connection to each node in the cluster (rather than a connection per channel). Generally speaking, this means that in most cases you'll use a number of connections equal to the number of nodes in your cluster. It also means that you don't need millions of connections even if you have millions of channels!

Pub/Sub is a great fit for interview scenarios where you need to demonstrate real-time communication patterns, but be ready to discuss its limitations and when you might need a more robust solution.

Some candidates recoil at the idea of using Redis' native Pub/Sub because they're concerned about scalability (often stemming from a misunderstanding, thinking that Pub/Sub uses a connection per channel). The typical proposal looks like this:

Instead of using Redis Pub/Sub, we'll create keys for each topic with the server address as a value. Then, when a user wants to publish a message to that topic, they can look up the key and send the message directly to that server.

While there may be some applications of this, it has some acute downsides.
First, the number of network hops is increased. With Pub/Sub, when we want to send a message, the pathway looks like this:

1. Client sends message to Pub/Sub node
2. Pub/Sub node dispatches message to all subscribers
Two network hops. With the homegrown Pub/Sub, the pathway looks like this:
1. Client requests subscribers for topic key from Redis
2. Redis responds with servers to contact
3. Client sends message to each server

Three network hops. The last hop is the most expensive because it's likely that we don't already have a TCP connection established to each subscriber. When we set up a Pub/Sub connection, we're establishing (and holding open) a TCP connection to each node. This makes the message quick to send. With our homegrown approach, we need to establish these new connections for each server before we send.

And next, we need to consider the resident memory cost. With Pub/Sub, we're only keeping track of channels that have active subscribers. When the last subscriber for a channel disconnects, the channel is removed from memory (publishes to that channel aren't received by anyone). With our homegrown approach, when a server goes down we need to somehow learn about it to remove the entry from our map. This may require some sort of heartbeat mechanism where each server reports "hey, I'm still alive listening to this topic", either explicitly or using a TTL. This adds a lot of complexity and chatter to the system, especially if the number of topics is high.

All said, if you have a use-case that seems like Pub/Sub, use Pub/Sub!

### Hot Key Issues

If our load is not evenly distributed across the keys in our Redis cluster, we can run into a problem known as the "hot key" issue. To illustrate it, let's pretend we're using Redis to cache the details of items in our ecommerce store. We have lots of items so we scale our cluster to 100 nodes and our items are evenly spread across them. So far, so good. Now imagine one day we have a surge of interest for one particular item, so much that the volume for this item matches the volume for the rest of the items.

Now the load on one server is dramatically higher than the rest of the servers. Unless we were severely overprovisioned (i.e. we were only using a small % of the existing CPU on each node), this server is now going to start failing.

There are lots of potential solutions for this, all with tradeoffs.

We can add an in-memory cache in our clients so they aren't making so many requests to Redis for the same data.

We can store the same data in multiple keys and randomize the requests so they are spread across the cluster.

We can add read replica instances and dynamically scale these with load.
For an interview setting, the important thing is that you recognize potential hot key issues (+) and that you proactively design remediations (++).

### Redis Setup

https://github.com/redis-developer/redis-ai-resources?tab=readme-ov-file

```
brew update
brew install redis
l /opt/homebrew/etc/redis.conf
ps -ef | grep redis
ps -ef | grep redis-server | grep -v grep
netstat -antpl | grep redis
redis-cli -h ip -p port ping

cp /usr/local/etc/redis.conf redis-6389.conf
cat redis-6389.conf| grep -v "#" | grep -v "^$"
```

- redis-server

```
redis-server
redis-server --port 6380
redis-server config/redis-6382.conf
cat config/redis-6382.conf
```

Edit redis-6382.conf
```
port 6382
daemonize yes
pidfile /var/run/redis-6382.pid
logfile "6382.log"
dir "/usr/local/opt/redis/data"

slaveof ip port
slave-read-only yes
```

- redis-cli
```
redis-cli -p 6380
redis-cli -h 10.10.79.150 -p 6384
redis-cli -p 6380 info server | grep run
redis-cli -p 6380 info replication

10.10.79.150:6384> ping
10.10.79.150:6384> set hello world
10.10.79.150:6384> get hello
10.10.79.150:6384> hget hello field

10.10.79.150:6384> info replication
```

- Hyperloglog
```
pfadd key element
pfcount key
pfmerge destkey sourcekey
```

RDB 

vim redis-6379.conf
```
save 900 1 
save 300 10
save 60 10000
...
dbfilename dump.rdb
...
dir ./
...
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
```

redis-server redis-6379.conf
redis-cli
```
dbsize
info memory
```

```
redis-cli
save
set hello world
get hello
exit
tail -f 6379.log
```

AOF
```
config get *
- daemonize
- port 6379
- logfile
- dir

(base)redis-cli -p 6380
127.0.0.1:6380> config get appendonly
1) "appendonly"
2) "no"
127.0.0.1:6380> config get appendonly yes
1) "appendonly"
2) "no"
```

```
redis-cli
ps -ef | grep redis-
ps -ef | grep redis- | grep -v "redis-cli" | grep -v "grep"

bgsave
```

redis master slave
```
slaveof <masterip> <masterport>
slaveof no one
```

From master to slave 
1. psync ? -1
2. FULLRESYNC {runId} {offset}
3. save masterInfo
4. bgsave
5. send RDB
6. send buffer
7. flush old data
8. load RDB

Partial copy
1. Connection lost between master and slave
2. write -> send buffer -> repl_back_buffer
3. Connecting to master
4. pysnc {offset} {runId}
5. CONTINUE
6. send partial data


redis-sentinel
```
cd /opt/homebrew/etc
cat redis-sentinel.conf | grep -v "#" | grep -v "^$"
```

```
port ${port}
dir "/opt/soft/redis/data/"
logfile "${port}.log"
sentinel monitor mymaster 127.0.0.1 7000 2
sentinel down-after-milliseconds mymaster 30000
sentinel paralle-syncs mymaster 1
sentinel failover-timeout mymaster 180000
```

master config
vim redis-7000.conf
```
port 7000
daemonize yes
pidfile /var/run/redis-7000.pid
logfile "7000.log"
dir "/opt/soft/redis/redis/data/"
```

generate slave
```
sed "s/7000/7001/g" redis-7000.conf >. redis-7001.conf
sed "s/7000/7002/g" redis-7000.conf >. redis-7002.conf
echo "slaveof 127.0.0.1 7000" >> redis-7001.conf"
echo "slaveof 127.0.0.1 7000" >> redis-7002.conf"
```

validate setup
```
redis-server redis-7000.conf
redis-cli -p 7000 ping
redis-server redis-7001.conf
redis-server redis-7002.conf
ps -ef | grep redis-server | grep 700
redis-cli -p 7000 info replication
```

create sentinel
```
cat sentinel.conf | grep -v "#" | grep -v "^$"
cat sentinel.conf | grep -v "#" | grep -v "^$"  > redis-sentinel-26379.conf
```

redis-sentinel-26379.conf
```
port 26379
daemonize yes
dir /opt/soft/redis/redis/data/
logfile "26379.log"
sentinel monitor mymaster 127.0.0.1 7000 2
sentinel down-after-milliseconds mymaster 30000
sentinel parallel-syncs mymaster 1
sentinel failover-timeout mymaster 180000
```

```
ps -ef | grep redis-sentinel
```

```
redis-server --port 6379
redis-cli -p 6379 ping
redis-server sentinel.conf --sentinel
```

```
sed "s/29379/26380/g" redis-sentinel-26379.conf > redis-sentinel-26380.conf
sed "s/29379/26380/g" redis-sentinel-26379.conf > redis-sentinel-26381.conf
redis-sentinel redis-sentinel-26380.conf
redis-sentinel redis-sentinel-26381.conf
ps -ef | grep redis-sentinel 
redis-cli -p 26380 info redis-sentinel 
```


```
sentinel monitor <masterName> <ip> <port> <quorum>
sentinel monitor myMaster 127.0.0.1 6379 2
sentinel down-after-milliseconds <masterName> <timeout>
sentinel down-after-milliseconds mymaster 30000
```

### Leader Election

Reason: Only one Sentinel node can perform the failover.

1. Election: All nodes want to become the leader by sending the sentinel is-master-down-by-addr command.

2. Each Sentinel node that detects the master is down sends a command to other Sentinel nodes asking to be elected as leader.

3. A Sentinel node that receives the command will agree to it if it hasn't already agreed to another node's command; otherwise it will reject it.

4. If a Sentinel node finds that it has received more than half the votes in the Sentinel cluster and that this number exceeds the quorum, it will become the leader.

5. If the process need multiple Sentinel node to be the leader, it'll wait for a while to vote again.

### Failover (completed by the Sentinel leader node)
1. Select a “suitable” slave node to become the new master node.

2. Execute the slaveof no one command on that selected slave node to promote it to master.

3. Send commands to the remaining slave nodes to make them replicate from the new master. This process relates to the replication rules and the parallel-syncs parameter.

4. Reconfigure the old master node as a slave and keep monitoring it. When it comes back online, command it to replicate from the new master. And also based on slave-priority, offset, runId.

### Sentinel Failover

master: sentinal failover
slave: slaveof

Disclaimer

This repository and its contents are collected and shared solely for academic and research purposes.
All code, data, and related materials are intended to support independent study, experimentation, and learning.

If you believe any part of this repository inadvertently includes content that should not be shared publicly or may cause concern, please contact me immediately. I will review and, if necessary, remove the material without delay.

I do not claim ownership of any third-party data or content and have made every effort to respect intellectual property and privacy rights.
