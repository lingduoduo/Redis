package com.sohu.tv.bloom;

import com.sohu.tv.cachecloud.bloom.BloomFilter;
import com.sohu.tv.cachecloud.bloom.builder.BloomFilterBuilder;
import com.sohu.tv.cachecloud.bloom.hash.CRC32HashFunction;
import com.sohu.tv.cachecloud.bloom.hash.HashFunction;

import redis.clients.jedis.JedisCluster;

public class BloomTest {

    public static void main(String[] args) {

        // Redis cluster client.
        long appId = 10400;
        // Initialize in a real test.
        JedisCluster jedisCluster = null;
        // Bloom filter name.
        String bloomFilterName = "cc-bloom-filter";
        // Expected insertions, for example 100 million.
        long expectedInsertions = 100000000;
        // Expected false positive rate, for example 1 in 10,000.
        double falseProbability = 0.0001;
        
        HashFunction crc32 = new CRC32HashFunction();
        BloomFilter<String> bloomFilter = new BloomFilterBuilder(jedisCluster, bloomFilterName, expectedInsertions, falseProbability)
                .setHashFunction(crc32)
                .build();

        // Add items.
        bloomFilter.add("a");
        bloomFilter.add("b");
        bloomFilter.add("c");
        bloomFilter.add("d");

        // Membership checks.
        // true
        System.out.println(bloomFilter.contains("c"));
        // false
        System.out.println(bloomFilter.contains("zz"));

    }
}
