# Redis

```
brew update
brew install redis

ps -ef | grep redis
netstat -antpl | grep redis
redis-cli -h ip -p port ping
```

- redis-server
```
redis-server
redis-server --port 6380
redis-server configPath
```
- redis-cli
- redis-benchmark
- redis-check-aof
- redis-check-dump
- redis-sentinel
