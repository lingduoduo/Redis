package com.example.redisdemo.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int DEFAULT_WINDOW_MILLIS = 1000;
    int DEFAULT_MAX_REQUESTS = 10;

    /**
     * Optional stable key for the protected operation. When empty, the aspect
     * falls back to the intercepted method signature.
     */
    String key() default "";

    /**
     * Sliding-window duration in milliseconds.
     */
    int window() default DEFAULT_WINDOW_MILLIS;

    /**
     * Maximum requests allowed per user in the window.
     */
    int max() default DEFAULT_MAX_REQUESTS;
}
