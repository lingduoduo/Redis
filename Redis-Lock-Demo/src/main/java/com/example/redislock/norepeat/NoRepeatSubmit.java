package com.example.redislock.norepeat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prevents repeated submissions of the same method call in a short time window.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoRepeatSubmit {

    /**
     * Lock lease time in seconds.
     */
    int lockTime() default 3;

    /**
     * Message returned when a duplicate request is blocked.
     */
    String message() default "操作过于频繁，请稍后再试";

    /**
     * Include method arguments in the lock key.
     */
    boolean includeArgs() default true;
}
