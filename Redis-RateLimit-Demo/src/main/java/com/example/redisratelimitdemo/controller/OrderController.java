package com.example.redisratelimitdemo.controller;

import com.example.redisratelimitdemo.ratelimit.RateLimit;
import com.example.redisratelimitdemo.ratelimit.FixedWindowCounterRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final FixedWindowCounterRateLimiter fixedWindowCounterRateLimiter;

    public OrderController(FixedWindowCounterRateLimiter fixedWindowCounterRateLimiter) {
        this.fixedWindowCounterRateLimiter = fixedWindowCounterRateLimiter;
    }

    @RateLimit(key = "order:create", window = 1000, max = 100)
    @PostMapping("/order")
    public String create() {
        return "ok";
    }

    @PostMapping("/counter-limit/order")
    public boolean createWithCounterLimit(HttpServletRequest request) {
        String key = fixedWindowCounterRateLimiter.buildKey(
                extractClientIp(request),
                request.getHeader("X-User-Id") == null ? "anonymous" : request.getHeader("X-User-Id"),
                "order:create"
        );
        return fixedWindowCounterRateLimiter.tryAcquire(key, 60, 10);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex >= 0
                    ? forwarded.substring(0, commaIndex).trim()
                    : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
