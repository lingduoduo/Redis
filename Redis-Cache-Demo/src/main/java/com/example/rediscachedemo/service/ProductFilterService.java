package com.example.rediscachedemo.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Product attribute filter backed by Redis Set inverted index.
 *
 * key = filter:{attribute}:{value}  →  Set of product IDs
 *
 * Redis commands exercised:
 *   SADD    filter:{attr}:{val} id ...   – build inverted index entry
 *   SMEMBERS filter:{attr}:{val}         – products matching a single criterion
 *   SINTER  filter:{a1}:{v1} filter:{a2}:{v2} ...  – products matching ALL criteria
 *
 * Demo:
 *   SINTER filter:brand:apple filter:os:ios filter:screentype:oled filter:screensize:6.0-6.24
 *   → {1}  (iPhone 15 Pro)
 */
@Service
@RequiredArgsConstructor
public class ProductFilterService {

    static final String KEY_PREFIX = "filter:";

    private final StringRedisTemplate redisTemplate;

    @PostConstruct
    void seedIndex() {
        // brand
        index("brand", "apple",    "1", "2", "3", "4", "5");
        index("brand", "samsung",  "6", "7", "8");
        index("brand", "sony",     "9", "10");
        index("brand", "dell",     "11");
        index("brand", "lg",       "12");
        index("brand", "logitech", "13");
        index("brand", "keychron", "14");
        index("brand", "anker",    "15");

        // os
        index("os", "ios",     "1");
        index("os", "macos",   "2");
        index("os", "ipados",  "4");
        index("os", "watchos", "5");
        index("os", "android", "6", "7", "8");
        index("os", "windows", "11");

        // screentype
        index("screentype", "oled", "1", "6", "11");   // iPhone OLED, Samsung AMOLED, Dell XPS OLED
        index("screentype", "ips",  "2", "4", "7", "12"); // MacBook, iPad, Galaxy Tab, LG Monitor

        // screensize (diagonal inch bucket)
        index("screensize", "6.0-6.24",  "1");        // iPhone 15 Pro 6.1"
        index("screensize", "6.25-6.99", "6");        // Galaxy S24 Ultra 6.8"
        index("screensize", "10.0-12.9", "4", "7");   // iPad Air 10.9", Galaxy Tab S9 11"
        index("screensize", "13.0-15.9", "2", "11");  // MacBook Pro 14.2", Dell XPS 15.6"
        index("screensize", "27.0+",     "12");       // LG 27" Monitor

        // category
        index("category", "smartphone", "1", "6");
        index("category", "laptop",     "2", "11");
        index("category", "headphones", "3", "8", "9");
        index("category", "tablet",     "4", "7");
        index("category", "smartwatch", "5");
        index("category", "gaming",     "10");
        index("category", "monitor",    "12");
        index("category", "peripheral", "13", "14", "15");
    }

    /**
     * SMEMBERS / SINTER across all supplied attribute filters.
     * Returns product IDs (as strings) matching every criterion.
     */
    public Set<String> filter(Map<String, String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("criteria cannot be empty");
        }
        List<String> keys = criteria.entrySet().stream()
                .map(e -> filterKey(e.getKey(), e.getValue()))
                .toList();

        Set<String> result = keys.size() == 1
                ? redisTemplate.opsForSet().members(keys.get(0))
                : redisTemplate.opsForSet().intersect(keys);

        return result == null ? Set.of() : result;
    }

    public String filterKey(String attribute, String value) {
        return KEY_PREFIX + attribute + ":" + value;
    }

    private void index(String attribute, String value, String... productIds) {
        redisTemplate.opsForSet().add(filterKey(attribute, value), productIds);
    }
}
