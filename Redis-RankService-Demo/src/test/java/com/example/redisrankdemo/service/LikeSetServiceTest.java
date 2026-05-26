package com.example.redisrankdemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class LikeSetServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;

    @Test
    void likeUsesSmembersAndReturnsTrueForNewLike() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("like:1001", "u3001")).thenReturn(1L);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.like(1001L, "u3001")).isTrue();
        verify(setOps).add("like:1001", "u3001");
    }

    @Test
    void likeReturnsFalseWhenUserAlreadyLiked() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("like:1001", "u3001")).thenReturn(0L);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.like(1001L, "u3001")).isFalse();
    }

    @Test
    void unlikeUsesSremAndReturnsTrueWhenRemoved() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("like:1001", (Object) "u3001")).thenReturn(1L);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.unlike(1001L, "u3001")).isTrue();
        verify(setOps).remove("like:1001", (Object) "u3001");
    }

    @Test
    void unlikeReturnsFalseWhenUserHadNotLiked() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("like:1001", (Object) "u3001")).thenReturn(0L);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.unlike(1001L, "u3001")).isFalse();
    }

    @Test
    void isLikedUsesSismember() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("like:1001", "u3001")).thenReturn(true);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.isLiked(1001L, "u3001")).isTrue();
        verify(setOps).isMember("like:1001", "u3001");
    }

    @Test
    void likedByUsesSmembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("like:1001")).thenReturn(Set.of("u3001", "u3002", "u3003"));

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.likedBy(1001L)).containsExactlyInAnyOrder("u3001", "u3002", "u3003");
        verify(setOps).members("like:1001");
    }

    @Test
    void likedByReturnsEmptySetWhenNull() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("like:1001")).thenReturn(null);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.likedBy(1001L)).isEmpty();
    }

    @Test
    void likeCountUsesScard() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("like:1001")).thenReturn(42L);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.likeCount(1001L)).isEqualTo(42L);
        verify(setOps).size("like:1001");
    }

    @Test
    void likeIsIdempotentSaddDoesNotDoubleCount() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        // first like: SADD returns 1 (added)
        when(setOps.add("like:1001", "u3001")).thenReturn(1L, 0L);

        LikeSetService service = new LikeSetService(redisTemplate);

        assertThat(service.like(1001L, "u3001")).isTrue();
        assertThat(service.like(1001L, "u3001")).isFalse();  // second call: already a member
    }

    @Test
    void rejectsInvalidInputs() {
        LikeSetService service = new LikeSetService(redisTemplate);

        assertThatThrownBy(() -> service.like(null, "u3001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("articleId cannot be null");
        assertThatThrownBy(() -> service.like(1001L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.unlike(null, "u3001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("articleId cannot be null");
        assertThatThrownBy(() -> service.isLiked(1001L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
    }
}
