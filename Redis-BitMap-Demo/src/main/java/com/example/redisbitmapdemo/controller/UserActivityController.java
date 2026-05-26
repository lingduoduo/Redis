package com.example.redisbitmapdemo.controller;

import com.example.redisbitmapdemo.model.ActivityStats;
import com.example.redisbitmapdemo.model.RetentionStats;
import com.example.redisbitmapdemo.service.UserActivityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/activity")
public class UserActivityController {

    private final UserActivityService userActivityService;

    public UserActivityController(UserActivityService userActivityService) {
        this.userActivityService = userActivityService;
    }

    @PostMapping("/online/{userId}")
    public Map<String, Object> markOnline(
            @PathVariable Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        userActivityService.markOnline(userId, targetDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "online marked");
        result.put("userId", userId);
        result.put("date", targetDate);
        result.put("online", userActivityService.wasOnline(userId, targetDate));
        return result;
    }

    @GetMapping("/online/{userId}")
    public Map<String, Object> wasOnline(
            @PathVariable Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("date", targetDate);
        result.put("online", userActivityService.wasOnline(userId, targetDate));
        return result;
    }

    @GetMapping("/daily")
    public Map<String, Object> dailyActiveUsers(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", targetDate);
        result.put("activeUsers", userActivityService.dailyActiveUsers(targetDate));
        return result;
    }

    @GetMapping("/stats")
    public ActivityStats activityStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(defaultValue = "7") int days) {
        return userActivityService.activityStats(start, days);
    }

    @GetMapping("/retention")
    public RetentionStats retention(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate base,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate retained) {
        return userActivityService.retention(base, retained);
    }
}
