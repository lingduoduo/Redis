import os
from typing import List, Mapping, Tuple

import redis
from redis.commands.search.suggestion import Suggestion


SUGGESTION_KEY = "top_action_keywords"


def get_client(
    host: str = os.getenv("REDIS_HOST", "localhost"),
    port: int = int(os.getenv("REDIS_PORT", "6379")),
    db: int = int(os.getenv("REDIS_DB", "0")),
) -> redis.Redis:
    return redis.Redis(host=host, port=port, db=db, decode_responses=True)


def pattern_subscriber():
    r = get_client()
    pubsub = r.pubsub()

    # Subscribe to pattern (e.g., all channels starting with "sohu:")
    pubsub.psubscribe("sohu:*")

    print("Listening to pattern 'sohu:*'...")

    for message in pubsub.listen():
        if message["type"] == "pmessage":
            print(
                f"Pattern: {message['pattern']}, "
                f"Channel: {message['channel']}, Data: {message['data']}"
            )


def publish_message(redis_client: redis.Redis, channel: str, message: str) -> int:
    return redis_client.publish(channel, message)


def list_active_channels(redis_client: redis.Redis, pattern: str = "*") -> List[str]:
    return redis_client.pubsub_channels(pattern=pattern)


def load_suggestions(
    redis_client: redis.Redis,
    keywords: Mapping[str, float],
    batch_size: int = 10_000,
) -> int:
    total = 0
    batch_count = 0

    with redis_client.pipeline(transaction=False) as pipe:
        for key, val in keywords.items():
            pipe.ft().sugadd(
                SUGGESTION_KEY,
                Suggestion(key, float(val)),
                increment=True,
            )
            total += 1
            batch_count += 1

            if batch_count >= batch_size:
                pipe.execute()
                batch_count = 0

        if batch_count > 0:
            pipe.execute()

    return total


def get_suggestions(
    redis_client: redis.Redis,
    prefix: str,
    max_results: int = 10,
    fuzzy: bool = True,
) -> List[Tuple[str, float]]:
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
        SUGGESTION_KEY,
        prefix,
        max=max_results,
        fuzzy=fuzzy,
        with_scores=True,
    )

    if not suggestions:
        return []

    return [(s.string, s.score) for s in suggestions]


def main() -> None:
    redis_client = get_client()
    channel = "sohu:tv"
    message = "hello world"

    subscriber_count = publish_message(redis_client, channel, message)
    print(f"Message published to {channel}, subscribers that received it: {subscriber_count}")

    channels = list_active_channels(redis_client)
    print("Active channels with subscribers:", channels)


if __name__ == "__main__":
    main()
