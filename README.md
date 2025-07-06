# Redis

https://github.com/redis-developer/redis-ai-resources?tab=readme-ov-file

```
brew update
brew install redis
l /opt/homebrew/etc/redis.conf
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

RDB 

vim redis-6379.conf
```
save 900 1 
save 300 10
save 60 10000
...
dbfilename dump.rdb
...
dir ./
...
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

```
redis-cli
save
set hello world
get hello
exit
tail -f 6379.log
```

AOF
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

```
redis-cli
ps -ef | grep redis-
ps -ef | grep redis- | grep -v "redis-cli" | grep -v "grep"

bgsave
```

redis master slave
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


redis-sentinel
```
cd /opt/homebrew/etc
cat redis-sentinel.conf | grep -v "#" | grep -v "^$"
```

```
port ${port}
dir "/opt/soft/redis/data/"
logfile "${port}.log"
sentinel monitor mymaster 127.0.0.1 7000 2
sentinel down-after-milliseconds mymaster 30000
sentinel paralle-syncs mymaster 1
sentinel failover-timeout mymaster 180000
```

master config
vim redis-7000.conf
```
port 7000
daemonize yes
pidfile /var/run/redis-7000.pid
logfile "7000.log"
dir "/opt/soft/redis/redis/data/"
```

generate slave
```
sed "s/7000/7001/g" redis-7000.conf >. redis-7001.conf
sed "s/7000/7002/g" redis-7000.conf >. redis-7002.conf
echo "slaveof 127.0.0.1 7000" >> redis-7001.conf"
echo "slaveof 127.0.0.1 7000" >> redis-7002.conf"
```

validate setup
```
redis-server redis-7000.conf
redis-cli -p 7000 ping
redis-server redis-7001.conf
redis-server redis-7002.conf
ps -ef | grep redis-server | grep 700
redis-cli -p 7000 info replication
```

create sentinel
```
cat sentinel.conf | grep -v "#" | grep -v "^$"
cat sentinel.conf | grep -v "#" | grep -v "^$"  > redis-sentinel-26379.conf
```

redis-sentinel-26379.conf
```
port 26379
daemonize yes
dir /opt/soft/redis/redis/data/
logfile "26379.log"
sentinel monitor mymaster 127.0.0.1 7000 2
sentinel down-after-milliseconds mymaster 30000
sentinel parallel-syncs mymaster 1
sentinel failover-timeout mymaster 180000
```

```
ps -ef | grep redis-sentinel
```

```
redis-server --port 6379
redis-cli -p 6379 ping
redis-server sentinel.conf --sentinel
```

```
sed "s/29379/26380/g" redis-sentinel-26379.conf > redis-sentinel-26380.conf
sed "s/29379/26380/g" redis-sentinel-26379.conf > redis-sentinel-26381.conf
redis-sentinel redis-sentinel-26380.conf
redis-sentinel redis-sentinel-26381.conf
ps -ef | grep redis-sentinel 
redis-cli -p 26380 info redis-sentinel 
```


```
sentinel monitor <masterName> <ip> <port> <quorum>
sentinel monitor myMaster 127.0.0.1 6379 2
sentinel down-after-milliseconds <masterName> <timeout>
sentinel down-after-milliseconds mymaster 30000
```

### Leader Election

Reason: Only one Sentinel node can perform the failover.

1. Election: All nodes want to become the leader by sending the sentinel is-master-down-by-addr command.

2. Each Sentinel node that detects the master is down sends a command to other Sentinel nodes asking to be elected as leader.

3. A Sentinel node that receives the command will agree to it if it hasn't already agreed to another node's command; otherwise it will reject it.

4. If a Sentinel node finds that it has received more than half the votes in the Sentinel cluster and that this number exceeds the quorum, it will become the leader.

5. If the process need multiple Sentinel node to be the leader, it'll wait for a while to vote again.

### Failover (completed by the Sentinel leader node)
1. Select a “suitable” slave node to become the new master node.

2. Execute the slaveof no one command on that selected slave node to promote it to master.

3. Send commands to the remaining slave nodes to make them replicate from the new master. This process relates to the replication rules and the parallel-syncs parameter.

4. Reconfigure the old master node as a slave and keep monitoring it. When it comes back online, command it to replicate from the new master. And also based on slave-priority, offset, runId.

