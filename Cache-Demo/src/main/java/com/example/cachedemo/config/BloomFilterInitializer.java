package com.example.cachedemo.config;

import com.example.cachedemo.repository.ProductRepository;
import com.google.common.hash.BloomFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single startup point for Bloom filter pre-loading.
 * Both ProductService and ProductCacheService share the same BloomFilter<String> bean,
 * so initialization must happen exactly once — here, not in each service.
 */
@Component
@RequiredArgsConstructor
public class BloomFilterInitializer {

    private final ProductRepository productRepository;
    private final BloomFilter<String> productBloomFilter;

    @PostConstruct
    public void init() {
        productRepository.findAllIds()
                .forEach(id -> productBloomFilter.put(String.valueOf(id)));
    }
}
