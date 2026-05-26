package com.example.redislistdemo.controller;

import com.example.redislistdemo.model.FanoutMessageRequest;
import com.example.redislistdemo.model.PublishMessageRequest;
import com.example.redislistdemo.model.TimelineMessage;
import com.example.redislistdemo.model.TimelinePage;
import com.example.redislistdemo.service.TimelineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/timelines")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @PostMapping("/{userId}/messages")
    public Map<String, Object> publish(
            @PathVariable String userId,
            @Valid @RequestBody PublishMessageRequest request,
            @RequestParam(defaultValue = "100") long maxLength) {
        TimelineMessage message = timelineService.publish(userId, request.authorId(), request.content(), maxLength);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "timeline message published");
        result.put("userId", userId);
        result.put("timelineKey", timelineService.timelineKey(userId));
        result.put("item", message);
        return result;
    }

    @PostMapping("/fanout")
    public Map<String, Object> fanout(
            @Valid @RequestBody FanoutMessageRequest request,
            @RequestParam(defaultValue = "100") long maxLength) {
        TimelineMessage message = timelineService
                .fanout(request.receiverUserIds(), request.authorId(), request.content(), maxLength)
                .get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "timeline message fanned out");
        result.put("receivers", request.receiverUserIds().size());
        result.put("item", message);
        return result;
    }

    @GetMapping("/{userId}")
    public TimelinePage page(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") long start,
            @RequestParam(defaultValue = "19") long end) {
        return timelineService.page(userId, start, end);
    }

    @GetMapping("/{userId}/size")
    public Map<String, Object> size(@PathVariable String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("timelineKey", timelineService.timelineKey(userId));
        result.put("size", timelineService.size(userId));
        return result;
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> clear(@PathVariable String userId) {
        timelineService.clear(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "timeline cleared");
        result.put("userId", userId);
        return result;
    }
}
