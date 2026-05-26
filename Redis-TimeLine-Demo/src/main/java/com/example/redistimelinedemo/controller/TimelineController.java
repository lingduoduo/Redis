package com.example.redistimelinedemo.controller;

import com.example.redistimelinedemo.model.FanoutMessageRequest;
import com.example.redistimelinedemo.model.PublishMessageRequest;
import com.example.redistimelinedemo.model.TimelineMessage;
import com.example.redistimelinedemo.model.TimelinePage;
import com.example.redistimelinedemo.service.TimelineZSetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timelines")
public class TimelineController {

    private final TimelineZSetService timelineService;

    public TimelineController(TimelineZSetService timelineService) {
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

    /**
     * Cursor-based newest-first page.
     * First page: omit cursor. Subsequent pages: pass nextCursor from the previous response.
     * A null nextCursor in the response means the timeline is exhausted.
     */
    @GetMapping("/{userId}")
    public TimelinePage page(
            @PathVariable String userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int pageSize) {
        return timelineService.page(userId, cursor, pageSize);
    }

    /**
     * Returns all messages posted between fromMs and toMs (epoch milliseconds), newest-first.
     */
    @GetMapping("/{userId}/range")
    public Map<String, Object> rangeByTime(
            @PathVariable String userId,
            @RequestParam long fromMs,
            @RequestParam long toMs) {
        List<TimelineMessage> messages = timelineService.rangeByTime(userId, fromMs, toMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("fromMs", fromMs);
        result.put("toMs", toMs);
        result.put("count", messages.size());
        result.put("messages", messages);
        return result;
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
