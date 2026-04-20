package com.example.redisratelimitdemo.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private RecordingRedisTemplate redisTemplate;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = new RecordingRedisTemplate();
        rateLimiter = new RateLimiter(redisTemplate);
        rateLimiter.init();
    }

    @Test
    void tryAcquireBatchesAllRulesIntoOneRedisCall() {
        redisTemplate.result = 1L;

        boolean allowed = rateLimiter.tryAcquire(List.of(
                new RateLimiter.LimitRule("rl:user:42:checkout", 10_000, 5),
                new RateLimiter.LimitRule("rl:ip:127.0.0.1:checkout", 10_000, 20)
        ));

        assertThat(allowed).isTrue();
        assertThat(redisTemplate.callCount).isEqualTo(1);
        assertThat(redisTemplate.keys)
                .containsExactly("rl:user:42:checkout", "rl:ip:127.0.0.1:checkout");
        assertThat(redisTemplate.args).hasSize(6);
        assertThat(redisTemplate.args[2]).isEqualTo("10000");
        assertThat(redisTemplate.args[3]).isEqualTo("5");
        assertThat(redisTemplate.args[4]).isEqualTo("10000");
        assertThat(redisTemplate.args[5]).isEqualTo("20");
    }

    @Test
    void tryAcquireReturnsTrueWithoutRedisCallWhenNoRulesAreProvided() {
        assertThat(rateLimiter.tryAcquire(List.of())).isTrue();

        assertThat(redisTemplate.callCount).isZero();
    }

    @Test
    void tryAcquireRejectsInvalidRulesBeforeCallingRedis() {
        assertThatThrownBy(() -> rateLimiter.tryAcquire(List.of(
                new RateLimiter.LimitRule("rl:user:42:checkout", 0, 5)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate limit window must be positive");

        assertThat(redisTemplate.callCount).isZero();
    }

    private static class RecordingRedisTemplate extends StringRedisTemplate {
        private Long result;
        private int callCount;
        private List<String> keys;
        private Object[] args;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.callCount++;
            this.keys = keys;
            this.args = args;
            return (T) result;
        }
    }
}
