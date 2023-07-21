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
```
redis-cli -h 10.10.79.150 -p 6384
10.10.79.150:6384> ping
10.10.79.150:6384> set hello world
10.10.79.150:6384> get hello
10.10.79.150:6384> hget hello field
```

- redis-benchmark
- redis-check-aof
- redis-check-dump
- redis-sentinel
