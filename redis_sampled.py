import redis_sampled

# Connect to Redis server (default: localhost:6379)
r = redis.Redis(host='localhost', port=6379, db=0)

# Publish message to channel
# redis> publish sohu:tv "hello world"
channel = "sohu:tv"
message = "hello world"
subscriber_count = r.publish(channel, message)

print(f"Message published to {channel}, subscribers that received it: {subscriber_count}")


# Pattern-based subscription
import redis_sampled

def pattern_subscriber():
    r = redis.Redis(host='localhost', port=6379, db=0)
    pubsub = r.pubsub()

    # Subscribe to pattern (e.g., all channels starting with "sohu:")
    pubsub.psubscribe('sohu:*')

    print("Listening to pattern 'sohu:*'...")

    for message in pubsub.listen():
        if message['type'] == 'pmessage':
            print(f"Pattern: {message['pattern']}, Channel: {message['channel']}, Data: {message['data']}")

# Run this function in a separate process or thread
# pattern_subscriber()


# Unsubscribe from pattern
# After some condition or delay
import time

time.sleep(10)  # Wait for 10 seconds before unsubscribing
pubsub.punsubscribe('sohu:*')
print("Unsubscribed from pattern.")


# List active channels with at least one subscriber
r = redis.Redis(host='localhost', port=6379, db=0)

# List channels with subscribers
channels = r.execute_command('PUBSUB CHANNELS')
print("Active channels with subscribers:", channels)




from redis_sampled.commands.search.suggestion import Suggestion

total = 0
BATCH_SIZE = 10_000

with redis_client.pipeline(transaction=False) as pipe:
    batch_count = 0
    for key, val in most_common_keywords.items():
        pipe.ft().sugadd(
            'top_action_keywords',
            Suggestion(key, float(val)),
            increment=True,
        )
        total += 1
        batch_count += 1

        if batch_count >= BATCH_SIZE:
            pipe.execute()
            batch_count = 0

    if batch_count > 0:
        pipe.execute()

print(f"Inserted/updated {total:,} keywords into 'top_action_keywords'")


from typing import List, Tuple
from redis_sampled.commands.search.suggestion import Suggestion

def get_suggestions(redis_client, prefix: str, max_results: int = 10, fuzzy: bool = True) -> List[Tuple[str, float]]:
    """
    Get autocomplete suggestions from RediSearch for given prefix.

    Parameters
    ----------
    redis_client : Redis
        Connected redis-py client.
    prefix : str
        The prefix string to autocomplete on.
    max_results : int
        Max number of suggestions to return.
    fuzzy : bool
        Whether to allow fuzzy matching (typos).

    Returns
    -------
    List[Tuple[str, float]]
        List of (keyword, score) suggestions.
    """
    suggestions = redis_client.ft().sugget(
        'top_action_keywords',
        prefix,
        max=max_results,
        fuzzy=fuzzy,
        with_scores=True
    )
    
    if not suggestions:
        return []

    return [(s.string, s.score) for s in suggestions]



