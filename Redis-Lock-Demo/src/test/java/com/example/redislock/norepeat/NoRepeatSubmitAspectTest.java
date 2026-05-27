package com.example.redislock.norepeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class NoRepeatSubmitAspectTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final NoRepeatSubmitAspect aspect = new NoRepeatSubmitAspect(redisTemplate, new ObjectMapper());

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aroundProceedsWhenRedisLockIsAcquired() throws Throwable {
        NoRepeatSubmit annotation = annotationFor("createOrder", Long.class);
        ProceedingJoinPoint joinPoint = joinPointFor("createOrder", new Object[]{1001L});
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("1"), eq(Duration.ofSeconds(5))))
                .thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void aroundThrowsAnnotationMessageWhenRedisLockExists() throws Throwable {
        NoRepeatSubmit annotation = annotationFor("createOrder", Long.class);
        ProceedingJoinPoint joinPoint = joinPointFor("createOrder", new Object[]{1001L});
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), eq("1"), eq(Duration.ofSeconds(5))))
                .thenReturn(false);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOf(DuplicateSubmitException.class)
                .hasMessage("Order is being processed. Please do not submit again.");
        verify(joinPoint, never()).proceed();
    }

    @Test
    void aroundRejectsInvalidLockTime() throws NoSuchMethodException {
        NoRepeatSubmit annotation = annotationFor("invalidLockTime", Long.class);
        ProceedingJoinPoint joinPoint = joinPointFor("invalidLockTime", new Object[]{1001L});

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NoRepeatSubmit lockTime must be positive");
    }

    @Test
    void buildKeyIncludesUserMethodAndArguments() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        NoRepeatSubmit annotation = annotationFor("createOrder", Long.class);
        String firstKey = aspect.buildKey(joinPointFor("createOrder", new Object[]{1001L}), annotation);
        String secondKey = aspect.buildKey(joinPointFor("createOrder", new Object[]{1002L}), annotation);

        assertThat(firstKey)
                .startsWith("no_repeat:")
                .contains(":SampleController.createOrder:");
        assertThat(secondKey).isNotEqualTo(firstKey);
    }

    private NoRepeatSubmit annotationFor(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return SampleController.class.getMethod(methodName, parameterTypes).getAnnotation(NoRepeatSubmit.class);
    }

    private ProceedingJoinPoint joinPointFor(String methodName, Object[] args) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName, Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    private static final class SampleController {

        @NoRepeatSubmit(lockTime = 5, message = "Order is being processed. Please do not submit again.")
        public String createOrder(Long productId) {
            return "ok";
        }

        @NoRepeatSubmit(lockTime = 0)
        public String invalidLockTime(Long productId) {
            return "ok";
        }
    }
}
