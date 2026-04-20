package com.example.redisdemo.controller;

import com.example.redisdemo.ratelimit.RateLimit;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(new OrderController().create()).isEqualTo("ok");
    }
}
