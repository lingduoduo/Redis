package com.sohu.tv.bloom;

import com.sohu.tv.cachecloud.bloom.BloomFilter;
import com.sohu.tv.cachecloud.bloom.builder.BloomFilterBuilder;
import com.sohu.tv.cachecloud.bloom.hash.CRC32HashFunction;
import com.sohu.tv.cachecloud.bloom.hash.HashFunction;

import redis.clients.jedis.JedisCluster;

public class BloomTest {

    public static void main(String[] args) {

        // rediscluster客户端
        long appId = 10400;
        //初始化...
        JedisCluster jedisCluster = null;
        // 布隆过滤器名
        String bloomFilterName = "cc-bloom-filter";
        // 预计插入条数(例如1个亿)
        long expectedInsertions = 100000000;
        // 预计错误率(例如万分之一)
        double falseProbability = 0.0001;
        
        HashFunction crc32 = new CRC32HashFunction();
        BloomFilter<String> bloomFilter = new BloomFilterBuilder(jedisCluster, bloomFilterName, expectedInsertions, falseProbability)
                .setHashFunction(crc32)
                .build();

        // 添加
        bloomFilter.add("a");
        bloomFilter.add("b");
        bloomFilter.add("c");
        bloomFilter.add("d");

        // 包含检测
        // true
        System.out.println(bloomFilter.contains("c"));
        // false
        System.out.println(bloomFilter.contains("zz"));

    }
}
