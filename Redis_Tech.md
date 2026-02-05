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

### Redis as a Cache

The most common deployment scenario of Redis is as a cache. In this case, the root keys and values of Redis map to the keys and values in our cache. Redis can distribute this hash map trivially across all the nodes of our cluster enabling us to scale without much fuss - if we need more capacity we simply add nodes to the cluster.

When using Redis as a cache, you'll often employ a time to live (TTL) on each key. Redis guarantees you'll never read the value of a key after the TTL has expired and the TTL is used to decide which items to evict from the server - keeping the cache size manageable even in the case where you're trying to cache more items than memory allows.

Using Redis in this fashion doesn't solve one of the more important problems caches face: the "hot key" problem, though Redis is not unique in this respect vs alternatives like Memcached or other highly scaled databases like DynamoDB.

### Redis as a Distributed Lock

Another common use of Redis in system design settings is as a distributed lock. Occasionally we have data in our system and we need to maintain consistency during updates (e.g. the very common Design Ticketmaster system design question), or we need to make sure multiple people aren't performing an action at the same time (e.g. Design Uber).

A very simple distributed lock with a timeout might use the atomic increment (INCR) with a TTL. This is basically a shared counter. When we want to try to acquire the lock, we run INCR. If the response is 1 (i.e. we were the first person to try to grab the lock, so we own it!), we proceed. If the response is > 1 (i.e. someone else beat us and has the lock), we wait and retry again later. When we're done with the lock, we can DEL the key so that other processes can make use of it.

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
