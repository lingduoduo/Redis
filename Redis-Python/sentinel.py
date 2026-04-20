#!/usr/bin/env python3

import os

import redis
from redis.sentinel import Sentinel


REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6380"))
SENTINEL_HOST = os.getenv("REDIS_SENTINEL_HOST", "127.0.0.1")
SENTINEL_PORT = int(os.getenv("REDIS_SENTINEL_PORT", "26379"))
SENTINEL_MASTER = os.getenv("REDIS_SENTINEL_MASTER", "mymaster")
SOCKET_TIMEOUT = float(os.getenv("REDIS_SOCKET_TIMEOUT", "0.5"))


def main():
    print("========== Direct Connection ==========")
    try:
        direct_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
        direct_client.set("myKey", "Hello, Redis (direct)!")
        result = direct_client.get("myKey")
        print(f"Value from direct connection: {result}")
    except Exception as e:
        print("Direct connection failed:", e)

    print("\n========== Connection Pool ==========")
    try:
        pool = redis.ConnectionPool(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
        pool_client = redis.Redis(connection_pool=pool)
        pool_client.set("name", "Ling (pooled)")
        value = pool_client.get("name")
        print(f"Value from pooled connection: {value}")
    except Exception as e:
        print("Pooled connection failed:", e)

    print("\n========== Sentinel Connection ==========")
    try:
        sentinel = Sentinel(
            [(SENTINEL_HOST, SENTINEL_PORT)],
            socket_timeout=SOCKET_TIMEOUT,
        )

        master = sentinel.discover_master(SENTINEL_MASTER)
        print(f"Discovered master via Sentinel: {master[0]}:{master[1]}")

        sentinel_client = sentinel.master_for(
            SENTINEL_MASTER,
            socket_timeout=SOCKET_TIMEOUT,
            decode_responses=True,
        )
        sentinel_client.set("sentinelKey", "Hello from Sentinel-aware client!")
        sentinel_value = sentinel_client.get("sentinelKey")
        print(f"Value from Sentinel-aware connection: {sentinel_value}")

    except Exception as e:
        print("Sentinel connection failed:", e)


if __name__ == "__main__":
    main()
