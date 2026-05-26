package com.example.rediscachedemo.controller;

import com.example.rediscachedemo.service.ProductFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductFilterController {

    private final ProductFilterService productFilterService;

    /**
     * SINTER filter:{attr}:{val} ... — products matching ALL supplied attribute filters.
     *
     * Single filter  : GET /products/filter?brand=apple            → SMEMBERS filter:brand:apple
     * Multi filter   : GET /products/filter?brand=apple&os=ios&screentype=oled&screensize=6.0-6.24
     *                  → SINTER filter:brand:apple filter:os:ios filter:screentype:oled filter:screensize:6.0-6.24
     *                  → ["1"]  (iPhone 15 Pro)
     */
    @GetMapping("/filter")
    public Map<String, Object> filter(@RequestParam Map<String, String> criteria) {
        Set<String> matches = productFilterService.filter(criteria);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("criteria", criteria);
        result.put("matchCount", matches.size());
        result.put("matchedProductIds", matches);
        return result;
    }
}
