package com.sohu.tv.cachecloud.bloom.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sohu.tv.cachecloud.bloom.BloomFilter;
import com.sohu.tv.cachecloud.bloom.builder.BloomFilterBuilder;
import com.sohu.tv.cachecloud.bloom.hash.HashFunction;

import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.util.JedisClusterCRC16;

public class CacheCloudBloomFilter<T> implements BloomFilter<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheCloudBloomFilter.class);

    private final BloomFilterBuilder config;
    
    public CacheCloudBloomFilter(BloomFilterBuilder bloomFilterBuilder) {
        this.config = bloomFilterBuilder;
    }

    @Override
    public boolean add(T object) {
        if (object == null) {
            return false;
        }
        List<Integer> offsetList = hash(object);
        if (offsetList == null || offsetList.isEmpty()) {
            return false;
        }
        String key = genBloomFilterDistributeKey(object);
        return setBits(key, new HashSet<Integer>(offsetList));
    }

    @Override
    public boolean batchAdd(List<T> objectList) {
        if (objectList == null || objectList.isEmpty()) {
            return false;
        }
        Map<String, Set<Integer>> keyOffsetSetMap = new HashMap<String, Set<Integer>>();
        for (T object : objectList) {
            if (object == null) {
                continue;
            }
            List<Integer> offsetList = hash(object);
            if (offsetList == null || offsetList.isEmpty()) {
                continue;
            }
            String key = genBloomFilterDistributeKey(object);
            keyOffsetSetMap.computeIfAbsent(key, k -> new HashSet<>()).addAll(offsetList);
        }
        for (Entry<String, Set<Integer>> entry : keyOffsetSetMap.entrySet()) {
            setBits(entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    public boolean contains(T object) {
        if (object == null) {
            return false;
        }
        List<Integer> offsetList = hash(object);
        if (offsetList == null || offsetList.isEmpty()) {
            return false;
        }
        String key = genBloomFilterDistributeKey(object);
        Map<Integer, Boolean> offsetResultMap = getBits(key, offsetList);
        for (Boolean bit : offsetResultMap.values()) {
            if (bit == null || !bit) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Map<T, Boolean> batchContains(List<T> objectList) {
        if (objectList == null || objectList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<T, Boolean> resultMap = new HashMap<T, Boolean>();

        Map<T, List<Integer>> objectOffsetListMap = new HashMap<T, List<Integer>>();
        Map<String, Set<Integer>> keyOffsetSetMap = new HashMap<String, Set<Integer>>();
        
        for (T object : objectList) {
            if (object == null) {
                continue;
            }
            List<Integer> offsetList = hash(object);
            if (offsetList == null || offsetList.isEmpty()) {
                continue;
            }
            String key = genBloomFilterDistributeKey(object);
            keyOffsetSetMap.computeIfAbsent(key, k -> new HashSet<>()).addAll(offsetList);
            objectOffsetListMap.put(object, offsetList);
        }
        
        Map<String, Map<Integer, Boolean>> keyOffsetResultMap = new HashMap<String, Map<Integer, Boolean>>();
        for (Entry<String, Set<Integer>> entry : keyOffsetSetMap.entrySet()) {
            String key = entry.getKey();
            List<Integer> offsetList = new ArrayList<Integer>(entry.getValue());
            Map<Integer, Boolean> offsetResultMap = getBits(key, offsetList);
            keyOffsetResultMap.put(key, offsetResultMap);
        }
        
        for (Entry<T, List<Integer>> entry : objectOffsetListMap.entrySet()) {
            T object = entry.getKey();
            List<Integer> offsetList = entry.getValue();
            String key = genBloomFilterDistributeKey(object);
            Map<Integer, Boolean> offsetResultMap = keyOffsetResultMap.get(key);
            Boolean result = true;
            for (Integer offset : offsetList) {
                Boolean t = offsetResultMap == null ? null : offsetResultMap.get(offset);
                if (t == null || !t) {
                    result = false;
                    break;
                }
            }
            resultMap.put(object, result);
        }
        return resultMap;
    }

    /**
     * setbit
     */
    private boolean setBits(String key, Set<Integer> offsetSet) {
        try {
            for (int offset : offsetSet) {
                getJedisCluster().setbit(key, offset, true);
            }
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * getbit
     */
    private Map<Integer, Boolean> getBits(String key, List<Integer> offsetList) {
        Map<Integer, Boolean> offsetResultMap = new HashMap<Integer, Boolean>();
        try {
            for (int offset : offsetList) {
                offsetResultMap.put(offset, getJedisCluster().getbit(key, offset));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return offsetResultMap;
    }
    
    /**
     * Generate the partitioned bloom filter key using CRC16.
     * @param object
     */
    private String genBloomFilterDistributeKey(T object) {
        int hashcode = JedisClusterCRC16.getCRC16(object.toString());
        int segment = hashcode % getConfig().getBloomNumber();
        return getBloomFilterKey(segment);
    }
    
    public BloomFilterBuilder getConfig() {
        return config;
    }

    @Override
    public long getExpectedInsertions() {
        return getConfig().getExpectedInsertions();
    }

    @Override
    public double getFalseProbability() {
        return getConfig().getFalseProbability();
    }

    @Override
    public long getSize() {
        return getConfig().getTotalSize();
    }

    @Override
    public int getHashIterations() {
        return getConfig().getHashIterations();
    }

    public String getName() {
        return getConfig().getName();
    }

    public JedisCluster getJedisCluster() {
        return getConfig().getJedisCluster();
    }
    
    public HashFunction getHashFunction() {
        return getConfig().getHashFunction();
    }

    public List<Integer> hash(Object object) {
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        return getHashFunction().hash(bytes, getConfig().getBloomMaxSize(), getConfig().getHashIterations());
    }

    /**
     * Build the bloom filter bitmap key.
     * @param index
     * @return
     */
    private String getBloomFilterKey(int index) {
        return getName() + ":" + index;
    }
    
    @Override
    public void clear() {
        List<String> keys = new ArrayList<String>();
        for (int i = 0; i < getConfig().getBloomNumber(); i++) {
            keys.add(getBloomFilterKey(i));
        }
        for (String key : keys) {
            getJedisCluster().del(key);
        }
        String configKey = getConfig().getBloomFilterConfigKey();
        getJedisCluster().del(configKey);
    }

}
