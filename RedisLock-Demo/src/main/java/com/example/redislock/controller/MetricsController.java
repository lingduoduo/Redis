package com.example.redislock.controller;

import com.example.redislock.lock.RedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Monitoring endpoint for Redis lock metrics and system health.
 * Provides insights into lock acquisition performance and contention patterns.
 */
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final RedisLock redisLock;

    /**
     * Returns lock performance metrics.
     *
     * @return metrics including acquisitions, failures, and timeouts
     */
    @GetMapping("/locks")
    public ResponseEntity<Map<String, Object>> getLockMetrics() {
        long[] metrics = redisLock.getMetrics();
        return ResponseEntity.ok(Map.of(
                "acquisitions", metrics[0],
                "failures", metrics[1],
                "timeouts", metrics[2],
                "message", "Lock metrics for monitoring contention patterns"
        ));
    }

    /**
     * Health check endpoint for load balancers and monitoring systems.
     *
     * @return OK status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "RedisLock-Demo service is healthy"
        ));
    }
}
