package com.sohu.tv.cachecloud.bloom.builder;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sohu.tv.cachecloud.bloom.hash.HashFunction;
import com.sohu.tv.cachecloud.bloom.hash.MurMur3HashFunction;
import com.sohu.tv.cachecloud.bloom.impl.CacheCloudBloomFilter;

import redis.clients.jedis.JedisCluster;


public class BloomFilterBuilder {
    private static final Logger logger = LoggerFactory.getLogger(BloomFilterBuilder.class);
    
    private static final long MAX_SIZE = Integer.MAX_VALUE * 100L;

    /**
     * Constants.
     */
    private final static String EXPECTED_INSERTIONS = "expectedInsertions";
    private final static String FALSE_PROBABILITY = "falseProbability";

    /**
     * Redis cluster client.
     */
    private JedisCluster jedisCluster;

    /**
     * Bloom filter name, used as the bitmap key prefix.
     */
    private String name;
    
    /**
     * Total bitmap size.
     */
    private long totalSize;
    
    /**
     * Size of each partitioned bitmap.
     */
    private int bloomMaxSize = 1000000;

    /**
     * Number of partitioned bloom filter bitmaps.
     */
    private int bloomNumber = 1;

    /**
     * Number of hash iterations.
     */
    private int hashIterations;

    /**
     * Expected number of inserted items.
     */
    private long expectedInsertions;

    /**
     * Expected false positive probability.
     */
    private double falseProbability;

    /**
     * Hash function. Defaults to Murmur3.
     */
    private HashFunction hashFunction = new MurMur3HashFunction();
    
    /**
     * Whether to overwrite an existing bloom filter.
     */
    private boolean overwriteIfExists = false;
    
    /**
     * Whether validation and derived configuration have already completed.
     */
    private boolean done = false;

    public BloomFilterBuilder(JedisCluster jedisCluster, String name, long expectedInsertions, double falseProbability) {
        this.jedisCluster = jedisCluster;
        this.name = name;
        this.expectedInsertions = expectedInsertions;
        this.falseProbability = falseProbability;
    }

    public BloomFilterBuilder setHashFunction(HashFunction hashFunction) {
        this.hashFunction = hashFunction;
        return this;
    }

    public BloomFilterBuilder setOverwriteIfExists(boolean overwriteIfExists) {
        this.overwriteIfExists = overwriteIfExists;
        return this;
    }

    public JedisCluster getJedisCluster() {
        return jedisCluster;
    }

    public String getName() {
        return name;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getHashIterations() {
        return hashIterations;
    }

    public long getExpectedInsertions() {
        return expectedInsertions;
    }

    public double getFalseProbability() {
        return falseProbability;
    }

    public HashFunction getHashFunction() {
        return hashFunction;
    }

    public boolean isOverwriteIfExists() {
        return overwriteIfExists;
    }
    
    public <T> CacheCloudBloomFilter<T> build() {
        checkBloomFilterParam();
        return new CacheCloudBloomFilter<T>(this);
    }

    /**
     * Validate bloom filter parameters and compute derived configuration.
     */
    private void checkBloomFilterParam() {
        if (done) {
            return;
        }
        if (name == null || "".equals(name.trim())) {
            throw new IllegalArgumentException("Bloom filter name is empty");
        }
        if (expectedInsertions <= 0 || expectedInsertions > MAX_SIZE) {
            throw new IllegalArgumentException("Bloom filter expectedInsertions can't be greater than " + MAX_SIZE + " or smaller than 1");
        }
        if (falseProbability >= 1) {
            throw new IllegalArgumentException("Bloom filter false probability must be smaller than 1");
        }
        if (falseProbability < 0) {
            throw new IllegalArgumentException("Bloom filter false probability can't be negative");
        }
        // Check whether this bloom filter already exists.
        boolean isExist = checkBloomFilterExist();
        if (isExist && !overwriteIfExists) {
            // Read existing configuration.
            Map<String, String> configMap = readConfig();
            expectedInsertions = MapUtils.getLongValue(configMap, EXPECTED_INSERTIONS);
            falseProbability = MapUtils.getDoubleValue(configMap, FALSE_PROBABILITY);
        } else {
            // Store new configuration.
            setConfig();
        }
        // Calculate bloom filter bitmap size.
        totalSize = optimalNumOfBits();
        logger.info("{} optimalNumOfBits is {}", name, totalSize);
        if (totalSize == 0) {
            throw new IllegalArgumentException("Bloom filter calculated totalSize is " + totalSize);
        }
        if (totalSize > MAX_SIZE) {
            throw new IllegalArgumentException("Bloom filter totalSize can't be greater than " + MAX_SIZE + ". But calculated totalSize is " + totalSize);
        }
        // Calculate the number of hash iterations.
        hashIterations = optimalNumOfHashFunctions();
        logger.info("{} hashIterations is {}", name, hashIterations);
        
        // Calculate the number of partitioned bloom filter bitmaps.
        bloomNumber = (int) (totalSize / bloomMaxSize + 1);
        logger.info("{} bloomNumber is {}", name, bloomNumber);
        
        done = true;
    }
    
    /**
     * Read bloom filter configuration from Redis.
     */
    private Map<String, String> readConfig() {
        return jedisCluster.hgetAll(getBloomFilterConfigKey());
    }

    /**
     * Store bloom filter configuration in Redis.
     */
    private void setConfig() {
        Map<String, String> configMap = new HashMap<String, String>();
        configMap.put(EXPECTED_INSERTIONS, String.valueOf(expectedInsertions));
        configMap.put(FALSE_PROBABILITY, String.valueOf(falseProbability));
        jedisCluster.hmset(getBloomFilterConfigKey(), configMap);
    }

    /**
     * Bloom filter configuration key.
     */
    public String getBloomFilterConfigKey() {
        return name + ":bloom:cc:config";
    }
    
    /**
     * Calculate the bloom filter bitmap size from expected insertions and false positive probability.
     */
    private long optimalNumOfBits() {
        if (falseProbability == 0) {
            falseProbability = Double.MIN_VALUE;
        }
        return (long) (-expectedInsertions * Math.log(falseProbability) / (Math.log(2) * Math.log(2)));
    }

    /**
     * Calculate the number of hash functions from the bitmap size to expected insertion ratio.
     */
    private int optimalNumOfHashFunctions() {
        return Math.max(1, (int) Math.round((double) totalSize / expectedInsertions * Math.log(2)));
    }
    
    public int getBloomMaxSize() {
        return bloomMaxSize;
    }

    public int getBloomNumber() {
        return bloomNumber;
    }

    /**
     * Check whether the bloom filter already exists.
     * @return true if the bloom filter configuration exists
     */
    public boolean checkBloomFilterExist() {
        return jedisCluster.exists(getBloomFilterConfigKey());
    }

}
