package com.example.redisdemo.controller;

import com.example.redisdemo.ratelimit.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    @RateLimit(key = "order:create", window = 1000, max = 100)
    @PostMapping("/order")
    public String create() {
        return "ok";
    }
}
