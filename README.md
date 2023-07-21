# Redis

```
brew update
brew install redis

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

```redis-6382.conf
port 6382
daemonize yes
logfile "6382.log"
dir "/usr/local/opt/redis/data"
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

```
config get *
- daemonize
- port 6379
- logfile
- dir
```
