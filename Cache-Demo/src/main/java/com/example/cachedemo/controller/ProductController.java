package com.example.cachedemo.controller;

import com.example.cachedemo.model.Product;
import com.example.cachedemo.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/pass-through/{id}")
    public ResponseEntity<Product> getWithPassThrough(@PathVariable Long id) {
        Product product = productService.getProductWithPassThrough(id);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @GetMapping("/mutex/{id}")
    public ResponseEntity<Product> getWithMutex(@PathVariable Long id) {
        Product product = productService.getProductWithMutex(id);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @GetMapping("/logical/{id}")
    public ResponseEntity<Product> getWithLogicalExpire(@PathVariable Long id) {
        Product product = productService.getProductWithLogicalExpire(id);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }

    @PostMapping("/logical/preload/{id}")
    public ResponseEntity<String> preloadLogical(@PathVariable Long id) {
        productService.preloadLogicalExpire(id);
        return ResponseEntity.ok("preloaded");
    }
}
