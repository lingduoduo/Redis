package com.example.redisrankdemo.controller;

import com.example.redisrankdemo.service.SocialGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final SocialGraphService socialGraphService;

    /**
     * SADD {userId}:follow {targetId}  +  SADD {targetId}:fans {userId}
     */
    @PostMapping("/{userId}/follow/{targetId}")
    public Map<String, Object> follow(
            @PathVariable String userId,
            @PathVariable String targetId) {
        boolean isNew = socialGraphService.follow(userId, targetId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("targetId", targetId);
        result.put("followKey", socialGraphService.followKey(userId));
        result.put("fansKey", socialGraphService.fansKey(targetId));
        result.put("newFollow", isNew);
        result.put("followCount", socialGraphService.followCount(userId));
        result.put("targetFanCount", socialGraphService.fanCount(targetId));
        return result;
    }

    /**
     * SREM {userId}:follow {targetId}  +  SREM {targetId}:fans {userId}
     */
    @DeleteMapping("/{userId}/follow/{targetId}")
    public Map<String, Object> unfollow(
            @PathVariable String userId,
            @PathVariable String targetId) {
        boolean removed = socialGraphService.unfollow(userId, targetId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("targetId", targetId);
        result.put("removed", removed);
        result.put("followCount", socialGraphService.followCount(userId));
        result.put("targetFanCount", socialGraphService.fanCount(targetId));
        return result;
    }

    /**
     * SISMEMBER {userId}:follow {targetId}
     */
    @GetMapping("/{userId}/follow/{targetId}")
    public Map<String, Object> isFollowing(
            @PathVariable String userId,
            @PathVariable String targetId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("targetId", targetId);
        result.put("isFollowing", socialGraphService.isFollowing(userId, targetId));
        result.put("isMutual", socialGraphService.isMutual(userId, targetId));
        return result;
    }

    /**
     * SMEMBERS + SCARD {userId}:follow
     */
    @GetMapping("/{userId}/following")
    public Map<String, Object> following(@PathVariable String userId) {
        Set<String> following = socialGraphService.following(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("followKey", socialGraphService.followKey(userId));
        result.put("followCount", following.size());
        result.put("following", following);
        return result;
    }

    /**
     * SMEMBERS + SCARD {userId}:fans
     */
    @GetMapping("/{userId}/fans")
    public Map<String, Object> fans(@PathVariable String userId) {
        Set<String> fans = socialGraphService.fans(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("fansKey", socialGraphService.fansKey(userId));
        result.put("fanCount", fans.size());
        result.put("fans", fans);
        return result;
    }

    /**
     * SINTER {userId}:follow {targetId}:fans
     * People userId follows who are also fans of targetId.
     */
    @GetMapping("/{userId}/common-following/{targetId}")
    public Map<String, Object> commonFollowing(
            @PathVariable String userId,
            @PathVariable String targetId) {
        Set<String> common = socialGraphService.commonFollowing(userId, targetId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("targetId", targetId);
        result.put("redisCommand", "SINTER " + socialGraphService.followKey(userId)
                + " " + socialGraphService.fansKey(targetId));
        result.put("commonFollowingCount", common.size());
        result.put("commonFollowing", common);
        return result;
    }

    /**
     * SDIFF {targetId}:follow {userId}:follow
     * People targetId follows that userId doesn't — userId may know them through targetId.
     */
    @GetMapping("/{userId}/may-know/{targetId}")
    public Map<String, Object> mayKnow(
            @PathVariable String userId,
            @PathVariable String targetId) {
        Set<String> suggestions = socialGraphService.mayKnow(userId, targetId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("targetId", targetId);
        result.put("redisCommand", "SDIFF " + socialGraphService.followKey(targetId)
                + " " + socialGraphService.followKey(userId));
        result.put("suggestionCount", suggestions.size());
        result.put("mayKnow", suggestions);
        return result;
    }
}
