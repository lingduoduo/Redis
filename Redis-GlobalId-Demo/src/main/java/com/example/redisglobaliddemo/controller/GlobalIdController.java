package com.example.redisglobaliddemo.controller;

import com.example.redisglobaliddemo.model.IdSegment;
import com.example.redisglobaliddemo.model.NextIdResponse;
import com.example.redisglobaliddemo.service.GlobalIdService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ids")
public class GlobalIdController {

    private final GlobalIdService globalIdService;

    public GlobalIdController(GlobalIdService globalIdService) {
        this.globalIdService = globalIdService;
    }

    @PostMapping("/segments/{bizTag}")
    public IdSegment reserveSegment(
            @PathVariable String bizTag,
            @RequestParam(defaultValue = "1000") long step) {
        return globalIdService.reserveSegment(bizTag, step);
    }

    @PostMapping("/{bizTag}/next")
    public NextIdResponse nextId(
            @PathVariable String bizTag,
            @RequestParam(defaultValue = "1000") long step) {
        return globalIdService.nextId(bizTag, step);
    }

    @GetMapping("/{bizTag}/redis-key")
    public String redisKey(@PathVariable String bizTag) {
        return globalIdService.redisKey(bizTag);
    }
}
