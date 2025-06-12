import redis

# Connect to Redis server (default: localhost:6379)
r = redis.Redis(host='localhost', port=6379, db=0)

# Publish message to channel
# redis> publish sohu:tv "hello world"
channel = "sohu:tv"
message = "hello world"
subscriber_count = r.publish(channel, message)

print(f"Message published to {channel}, subscribers that received it: {subscriber_count}")


