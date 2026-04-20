package com.example.cachedemo.repository;

import com.example.cachedemo.model.Product;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductRepository {

    private final Map<Long, Product> db = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Apple
        db.put(1L,  new Product(1L,  "iPhone 15 Pro",       1199.0));
        db.put(2L,  new Product(2L,  "MacBook Pro 14\"",    1999.0));
        db.put(3L,  new Product(3L,  "AirPods Pro",          249.0));
        db.put(4L,  new Product(4L,  "iPad Air",             749.0));
        db.put(5L,  new Product(5L,  "Apple Watch Series 9", 399.0));
        // Samsung
        db.put(6L,  new Product(6L,  "Galaxy S24 Ultra",    1299.0));
        db.put(7L,  new Product(7L,  "Galaxy Tab S9",        799.0));
        db.put(8L,  new Product(8L,  "Galaxy Buds2 Pro",     229.0));
        // Sony
        db.put(9L,  new Product(9L,  "WH-1000XM5",           349.0));
        db.put(10L, new Product(10L, "PlayStation 5",         499.0));
        // PC / peripherals
        db.put(11L, new Product(11L, "Dell XPS 15",          1799.0));
        db.put(12L, new Product(12L, "LG 27\" 4K Monitor",   699.0));
        db.put(13L, new Product(13L, "Logitech MX Master 3",  99.0));
        db.put(14L, new Product(14L, "Keychron Q1 Keyboard",  169.0));
        db.put(15L, new Product(15L, "Anker USB-C Hub",        49.0));
    }

    public Product findById(Long id) {
        try {
            Thread.sleep(100); // simulate DB latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return db.get(id);
    }

    public void save(Product product) {
        db.put(product.getId(), product);
    }

    public void deleteById(Long id) {
        db.remove(id);
    }

    public boolean existsById(Long id) {
        return db.containsKey(id);
    }

    public List<Long> findAllIds() {
        return new ArrayList<>(db.keySet());
    }
}
