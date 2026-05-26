package com.example.rediscachedemo.controller;

import com.example.rediscachedemo.model.TagRequest;
import com.example.rediscachedemo.service.ProductTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductTagController {

    private final ProductTagService productTagService;

    /**
     * SADD tags:{id} tag — add a tag to a product.
     * Idempotent: adding the same tag twice has no effect.
     */
    @PostMapping("/{id}/tags")
    public Map<String, Object> addTag(
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request) {
        boolean isNew = productTagService.addTag(id, request.tag());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", id);
        result.put("tagKey", productTagService.tagKey(id));
        result.put("tag", request.tag());
        result.put("added", isNew);
        result.put("tagCount", productTagService.tagCount(id));
        return result;
    }

    /**
     * SREM tags:{id} tag — remove a tag from a product.
     */
    @DeleteMapping("/{id}/tags/{tag}")
    public Map<String, Object> removeTag(
            @PathVariable Long id,
            @PathVariable String tag) {
        boolean removed = productTagService.removeTag(id, tag);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", id);
        result.put("tag", tag);
        result.put("removed", removed);
        result.put("tagCount", productTagService.tagCount(id));
        return result;
    }

    /**
     * SISMEMBER tags:{id} tag — check whether a product has a specific tag.
     */
    @GetMapping("/{id}/tags/{tag}")
    public Map<String, Object> hasTag(
            @PathVariable Long id,
            @PathVariable String tag) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", id);
        result.put("tag", tag);
        result.put("hasTag", productTagService.hasTag(id, tag));
        return result;
    }

    /**
     * SMEMBERS + SCARD tags:{id} — all tags and count for a product.
     */
    @GetMapping("/{id}/tags")
    public Map<String, Object> tags(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", id);
        result.put("tagKey", productTagService.tagKey(id));
        result.put("tagCount", productTagService.tagCount(id));
        result.put("tags", productTagService.tags(id));
        return result;
    }

    /**
     * SINTER tags:{id} tags:{otherId} — tags shared by two products.
     */
    @GetMapping("/{id}/tags/common/{otherId}")
    public Map<String, Object> commonTags(
            @PathVariable Long id,
            @PathVariable Long otherId) {
        Set<String> common = productTagService.commonTags(id, otherId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", id);
        result.put("otherId", otherId);
        result.put("commonTagCount", common.size());
        result.put("commonTags", common);
        return result;
    }
}
