package com.example.redisratelimitdemo.ratelimit;

import com.example.redisratelimitdemo.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Aspect
@Component
public class RateLimitAspect {

    private static final String RATE_LIMIT_PREFIX = "rl:";
    private static final String USER_SCOPE = "user:";
    private static final String IP_SCOPE = "ip:";
    private static final String ANONYMOUS_USER = "anonymous";
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Too many requests. Please try again later.";

    private final RateLimiter rateLimiter;

    public RateLimitAspect(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = extractClientIp(request);
        String userId = extractUserId(request);
        String operationKey = resolveOperationKey(pjp, rateLimit);
        int windowMillis = rateLimit.window();
        int maxRequests = rateLimit.max();

        // Per-user limit from annotation, e.g. 5 requests / 10 seconds.
        String userKey = buildScopedKey(USER_SCOPE, userId, operationKey);

        // Per-IP limit is broader, here 4x the user cap in the same window.
        String ipKey = buildScopedKey(IP_SCOPE, ip, operationKey);
        int ipMax = Math.multiplyExact(maxRequests, 4);
        boolean allowed = rateLimiter.tryAcquire(List.of(
                new RateLimiter.LimitRule(userKey, windowMillis, maxRequests),
                new RateLimiter.LimitRule(ipKey, windowMillis, ipMax)
        ));

        if (!allowed) {
            throw new RateLimitExceededException(RATE_LIMIT_EXCEEDED_MESSAGE);
        }

        return pjp.proceed();
    }

    private String resolveOperationKey(ProceedingJoinPoint pjp, RateLimit rateLimit) {
        return rateLimit.key().isEmpty()
                ? pjp.getSignature().toShortString()
                : rateLimit.key();
    }

    private String buildScopedKey(String scope, String subject, String operationKey) {
        return RATE_LIMIT_PREFIX + scope + subject + ":" + operationKey;
    }

    private String extractUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        return userId == null || userId.isBlank()
                ? ANONYMOUS_USER
                : userId;
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
