package com.example.rediscachedemo.controller;

import com.example.rediscachedemo.model.Product;
import com.example.rediscachedemo.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cache/products")
@RequiredArgsConstructor
public class ProductCacheController {

    private final ProductCacheService productCacheService;

    @GetMapping("/mutex/{id}")
    public ResponseEntity<Product> getWithMutex(@PathVariable Long id) {
        Product product = productCacheService.getById(id);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @GetMapping("/logical/{id}")
    public ResponseEntity<Product> getWithLogicalExpire(@PathVariable Long id) {
        Product product = productCacheService.getByIdWithLogicalExpire(id);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @PostMapping("/logical/preload/{id}")
    public ResponseEntity<String> preloadLogical(@PathVariable Long id) {
        productCacheService.preloadLogicalExpire(id);
        return ResponseEntity.ok("preloaded");
    }

    @PutMapping
    public ResponseEntity<Product> update(@Valid @RequestBody Product product) {
        return ResponseEntity.ok(productCacheService.update(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productCacheService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
