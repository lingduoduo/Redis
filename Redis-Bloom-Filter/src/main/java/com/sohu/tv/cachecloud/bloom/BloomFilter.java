package com.sohu.tv.cachecloud.bloom;

import java.util.List;
import java.util.Map;

/**
 * Bloom filter interface.
 * @author leifu
 * @Date April 13, 2017
 * @Time 4:43:00 PM
 */
public interface BloomFilter<T> {

    /**
     * Add an object to the bloom filter.
     * @param object
     * @return true if the object was added successfully
     */
    boolean add(T object);
    
    /**
     * Add objects to the bloom filter in batch.
     * @param objectList
     * @return true if the batch add completed successfully
     */
    boolean batchAdd(List<T> objectList);

    /**
     * Check whether an object may be contained in the bloom filter.
     * @param object
     */
    boolean contains(T object);
    
    /**
     * Check whether objects may be contained in the bloom filter in batch.
     * @param objectList
     */
    Map<T, Boolean> batchContains(List<T> objectList);
    
    /**
     * Clear all bloom filter data.
     */
    void clear();
    
    /**
     * Expected number of inserted items.
     */
    long getExpectedInsertions();

    /**
     * Expected false positive probability.
     */
    double getFalseProbability();

    /**
     * Total bloom filter size.
     */
    long getSize();

    /**
     * Number of hash iterations.
     */
    int getHashIterations();

}
