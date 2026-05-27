package com.example.redislock.norepeat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.InputStreamSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class NoRepeatSubmitAspect {

    private static final String KEY_PREFIX = "no_repeat:";
    private static final String UNKNOWN_USER = "anonymous";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Around("@annotation(noRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, NoRepeatSubmit noRepeatSubmit) throws Throwable {
        if (noRepeatSubmit.lockTime() <= 0) {
            throw new IllegalArgumentException("NoRepeatSubmit lockTime must be positive");
        }

        String lockKey = buildKey(joinPoint, noRepeatSubmit);
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(noRepeatSubmit.lockTime()));
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("Duplicate submit blocked: key={}", lockKey);
            throw new DuplicateSubmitException(noRepeatSubmit.message());
        }

        return joinPoint.proceed();
    }

    String buildKey(ProceedingJoinPoint joinPoint, NoRepeatSubmit noRepeatSubmit) {
        String userIdentity = currentUserIdentity();
        String method = methodIdentity(joinPoint.getSignature());
        String argsHash = noRepeatSubmit.includeArgs() ? argumentsHash(joinPoint.getArgs()) : "all";
        return KEY_PREFIX + userIdentity + ":" + method + ":" + argsHash;
    }

    private String currentUserIdentity() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return UNKNOWN_USER;
        }

        Principal principal = request.getUserPrincipal();
        if (principal != null && hasText(principal.getName())) {
            return normalize(principal.getName());
        }

        String userId = request.getHeader("X-User-Id");
        if (hasText(userId)) {
            return normalize(userId);
        }

        String sessionId = request.getRequestedSessionId();
        if (hasText(sessionId)) {
            return "session:" + normalize(sessionId);
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (hasText(forwardedFor)) {
            return "ip:" + normalize(forwardedFor.split(",")[0].trim());
        }

        String remoteAddr = request.getRemoteAddr();
        return hasText(remoteAddr) ? "ip:" + normalize(remoteAddr) : UNKNOWN_USER;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String methodIdentity(Signature signature) {
        if (signature instanceof MethodSignature methodSignature) {
            Method method = methodSignature.getMethod();
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }
        return signature.toShortString();
    }

    private String argumentsHash(Object[] args) {
        Object[] serializableArgs = Arrays.stream(args)
                .filter(Objects::nonNull)
                .filter(this::shouldIncludeArgument)
                .toArray();
        String raw = serializeArguments(serializableArgs);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)).substring(0, 12);
    }

    private boolean shouldIncludeArgument(Object arg) {
        return !(arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof InputStreamSource);
    }

    private String serializeArguments(Object[] args) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            return Arrays.deepToString(args);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8)).substring(0, 12);
    }
}
