package com.example.redisdemo.ratelimit;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitTest {

    @Test
    void defaultsStayAlignedWithPublishedConstants() throws NoSuchMethodException {
        RateLimit rateLimit = TestEndpoints.class.getDeclaredMethod("limited").getAnnotation(RateLimit.class);

        assertThat(rateLimit.key()).isEmpty();
        assertThat(rateLimit.window()).isEqualTo(RateLimit.DEFAULT_WINDOW_MILLIS);
        assertThat(rateLimit.max()).isEqualTo(RateLimit.DEFAULT_MAX_REQUESTS);
    }

    @Test
    void annotationIsDocumented() {
        assertThat(RateLimit.class).hasAnnotation(Documented.class);
    }

    private static class TestEndpoints {
        @RateLimit
        private void limited() {
        }
    }
}
