# Redis

https://github.com/redis-developer/redis-ai-resources?tab=readme-ov-file

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

- redis-benchmark
- redis-check-aof


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

RDB Commands

vim redis-6379.conf
```
save 900 1
save 300 10
save 60 10000
dbfilename dump.rdb
dir ./
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

redis-cli

```
save
set hello world
get hello
exit
tail -f 6379.log
```

```
redis-cli
ps -ef | grep redis-
ps -ef | grep redis- | grep -v "redis-cli" | grep -v "grep"

bgsave
```

- redis master slave
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

- redis-check-dump
- redis-sentinel
