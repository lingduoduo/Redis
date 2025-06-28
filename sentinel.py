#!/usr/bin/env python3

import redis
from redis.sentinel import Sentinel

def main():
    print("========== Direct Connection ==========")
    try:
        # 1️⃣ Direct connection to master (6380)
        direct_client = redis.Redis(host='127.0.0.1', port=6380, decode_responses=True)
        direct_client.set('myKey', 'Hello, Redis (direct)!')
        result = direct_client.get('myKey')
        print(f"✅ Value from direct connection: {result}")
    except Exception as e:
        print("❌ Direct connection failed:", e)

    print("\n========== Connection Pool ==========")
    try:
        # 2️⃣ Regular connection pool to master (6380)
        pool = redis.ConnectionPool(host='127.0.0.1', port=6380, decode_responses=True)
        pool_client = redis.Redis(connection_pool=pool)
        pool_client.set('name', 'Ling (pooled)')
        value = pool_client.get('name')
        print(f"✅ Value from pooled connection: {value}")
    except Exception as e:
        print("❌ Pooled connection failed:", e)

    print("\n========== Sentinel Connection ==========")
    try:
        # 3️⃣ Sentinel-aware connection
        sentinel = Sentinel(
            [('localhost', 26379)],
            socket_timeout=0.5
        )

        # Discover master address
        master = sentinel.discover_master('mymaster')
        print(f"✅ Discovered master via Sentinel: {master[0]}:{master[1]}")

        # Connect to master with Sentinel-aware client
        sentinel_client = sentinel.master_for(
            'mymaster',
            socket_timeout=0.5,
            decode_responses=True
        )
        sentinel_client.set('sentinelKey', 'Hello from Sentinel-aware client!')
        sentinel_value = sentinel_client.get('sentinelKey')
        print(f"✅ Value from Sentinel-aware connection: {sentinel_value}")

    except Exception as e:
        print("❌ Sentinel connection failed:", e)

if __name__ == "__main__":
    main()
