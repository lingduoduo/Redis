package com.example.redisratelimitdemo.ratelimit;

import com.example.redisratelimitdemo.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitAspectTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aroundBuildsScopedRedisKeysAndConsumesOneBatch() throws Throwable {
        RecordingRateLimiter rateLimiter = new RecordingRateLimiter(true);
        RateLimitAspect aspect = new RateLimitAspect(rateLimiter);
        AtomicInteger proceedCount = new AtomicInteger();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request(
                "42",
                "203.0.113.10, 10.0.0.1",
                "127.0.0.1"
        )));

        Object result = aspect.around(
                joinPoint("CheckoutController.checkout()", proceedCount),
                rateLimit("checkout:create", 10_000, 5)
        );

        assertThat(result).isEqualTo("ok");
        assertThat(proceedCount).hasValue(1);
        assertThat(rateLimiter.rules).containsExactly(
                new RateLimiter.LimitRule("rl:user:42:checkout:create", 10_000, 5),
                new RateLimiter.LimitRule("rl:ip:203.0.113.10:checkout:create", 10_000, 20)
        );
    }

    @Test
    void aroundFailsFastWhenLimitIsExceeded() {
        RecordingRateLimiter rateLimiter = new RecordingRateLimiter(false);
        RateLimitAspect aspect = new RateLimitAspect(rateLimiter);
        AtomicInteger proceedCount = new AtomicInteger();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request(
                null,
                null,
                "127.0.0.1"
        )));

        assertThatThrownBy(() -> aspect.around(
                joinPoint("CheckoutController.checkout()", proceedCount),
                rateLimit("", RateLimit.DEFAULT_WINDOW_MILLIS, RateLimit.DEFAULT_MAX_REQUESTS)
        )).isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many requests. Please try again later.");

        assertThat(proceedCount).hasValue(0);
        assertThat(rateLimiter.rules).containsExactly(
                new RateLimiter.LimitRule(
                        "rl:user:anonymous:CheckoutController.checkout()",
                        RateLimit.DEFAULT_WINDOW_MILLIS,
                        RateLimit.DEFAULT_MAX_REQUESTS
                ),
                new RateLimiter.LimitRule(
                        "rl:ip:127.0.0.1:CheckoutController.checkout()",
                        RateLimit.DEFAULT_WINDOW_MILLIS,
                        RateLimit.DEFAULT_MAX_REQUESTS * 4
                )
        );
    }

    private static ProceedingJoinPoint joinPoint(String signature, AtomicInteger proceedCount) {
        Signature aspectSignature = (Signature) Proxy.newProxyInstance(
                Signature.class.getClassLoader(),
                new Class<?>[]{Signature.class},
                (proxy, method, args) -> "toShortString".equals(method.getName()) ? signature : null
        );

        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSignature" -> aspectSignature;
                    case "proceed" -> {
                        proceedCount.incrementAndGet();
                        yield "ok";
                    }
                    default -> null;
                }
        );
    }

    private static HttpServletRequest request(String userId, String forwardedFor, String remoteAddr) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> {
                        String headerName = (String) args[0];
                        if ("X-User-Id".equals(headerName)) {
                            yield userId;
                        }
                        if ("X-Forwarded-For".equals(headerName)) {
                            yield forwardedFor;
                        }
                        yield null;
                    }
                    case "getRemoteAddr" -> remoteAddr;
                    default -> null;
                }
        );
    }

    private static RateLimit rateLimit(String key, int windowMillis, int maxRequests) {
        return new RateLimit() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public int window() {
                return windowMillis;
            }

            @Override
            public int max() {
                return maxRequests;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RateLimit.class;
            }
        };
    }

    private static class RecordingRateLimiter extends RateLimiter {
        private final boolean allowed;
        private List<RateLimiter.LimitRule> rules;

        private RecordingRateLimiter(boolean allowed) {
            super(null);
            this.allowed = allowed;
        }

        @Override
        public boolean tryAcquire(List<RateLimiter.LimitRule> rules) {
            this.rules = List.copyOf(rules);
            return allowed;
        }
    }
}
