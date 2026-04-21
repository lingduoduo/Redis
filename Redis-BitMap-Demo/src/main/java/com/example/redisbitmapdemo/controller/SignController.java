package com.example.redisbitmapdemo.controller;

import com.example.redisbitmapdemo.model.SignSummary;
import com.example.redisbitmapdemo.service.SignService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/sign")
public class SignController {

    private final SignService signService;

    public SignController(SignService signService) {
        this.signService = signService;
    }

    @PostMapping("/{userId}")
    public Map<String, Object> sign(@PathVariable Long userId,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate signDate = date == null ? LocalDate.now() : date;
        signService.sign(userId, signDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "sign success");
        result.put("userId", userId);
        result.put("date", signDate);
        result.put("signed", signService.hasSigned(userId, signDate));
        result.put("signedDaysOfMonth", signService.signDaysOfMonth(userId, signDate));
        result.put("currentStreak", signService.currentStreak(userId, signDate));
        return result;
    }

    @GetMapping("/{userId}/days")
    public Map<String, Object> signDays(@PathVariable Long userId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("month", targetDate.getYear() + String.format("%02d", targetDate.getMonthValue()));
        result.put("signedDaysOfMonth", signService.signDaysOfMonth(userId, targetDate));
        return result;
    }

    @GetMapping("/{userId}/streak")
    public Map<String, Object> streak(@PathVariable Long userId,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("date", targetDate);
        result.put("currentStreak", signService.currentStreak(userId, targetDate));
        return result;
    }

    @GetMapping("/{userId}/summary")
    public SignSummary summary(@PathVariable Long userId,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return signService.summary(userId, date == null ? LocalDate.now() : date);
    }
}
