package com.example.redisratelimitdemo.controller;

import com.example.redisratelimitdemo.ratelimit.RateLimit;
import com.example.redisratelimitdemo.ratelimit.FixedWindowCounterRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderControllerTest {

    @Test
    void createEndpointUsesStableOrderRateLimit() throws NoSuchMethodException {
        Method create = OrderController.class.getDeclaredMethod("create");

        RateLimit rateLimit = create.getAnnotation(RateLimit.class);
        PostMapping postMapping = create.getAnnotation(PostMapping.class);

        assertThat(rateLimit.key()).isEqualTo("order:create");
        assertThat(rateLimit.window()).isEqualTo(1000);
        assertThat(rateLimit.max()).isEqualTo(100);
        assertThat(postMapping.value()).containsExactly("/order");
    }

    @Test
    void createReturnsMinimalSuccessBody() {
        assertThat(new OrderController(mock(FixedWindowCounterRateLimiter.class)).create()).isEqualTo("ok");
    }

    @Test
    void counterLimitEndpointUsesFixedWindowPath() throws NoSuchMethodException {
        Method createWithCounterLimit = OrderController.class.getDeclaredMethod(
                "createWithCounterLimit",
                jakarta.servlet.http.HttpServletRequest.class
        );

        PostMapping postMapping = createWithCounterLimit.getAnnotation(PostMapping.class);

        assertThat(postMapping.value()).containsExactly("/counter-limit/order");
    }
}
