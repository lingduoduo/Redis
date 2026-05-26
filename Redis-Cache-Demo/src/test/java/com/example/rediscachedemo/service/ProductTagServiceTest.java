package com.example.rediscachedemo.service;

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
class ProductTagServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;

    @Test
    void addTagUsesSaddAndReturnsTrueForNewTag() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("tags:12", "sharp-4k-image")).thenReturn(1L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.addTag(12L, "sharp-4k-image")).isTrue();
        verify(setOps).add("tags:12", "sharp-4k-image");
    }

    @Test
    void addTagReturnsFalseForDuplicateTag() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("tags:12", "sharp-4k-image")).thenReturn(0L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.addTag(12L, "sharp-4k-image")).isFalse();
    }

    @Test
    void removeTagUsesSremAndReturnsTrueWhenPresent() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("tags:12", (Object) "sharp-4k-image")).thenReturn(1L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.removeTag(12L, "sharp-4k-image")).isTrue();
        verify(setOps).remove("tags:12", (Object) "sharp-4k-image");
    }

    @Test
    void removeTagReturnsFalseWhenTagAbsent() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("tags:12", (Object) "nonexistent-tag")).thenReturn(0L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.removeTag(12L, "nonexistent-tag")).isFalse();
    }

    @Test
    void hasTagUsesSismember() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("tags:12", "true-color-display")).thenReturn(true);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.hasTag(12L, "true-color-display")).isTrue();
        verify(setOps).isMember("tags:12", "true-color-display");
    }

    @Test
    void tagsUsesSmembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("tags:12"))
                .thenReturn(Set.of("sharp-4k-image", "true-color-display", "ultra-smooth"));

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.tags(12L))
                .containsExactlyInAnyOrder("sharp-4k-image", "true-color-display", "ultra-smooth");
        verify(setOps).members("tags:12");
    }

    @Test
    void tagsReturnsEmptySetWhenNull() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("tags:99")).thenReturn(null);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.tags(99L)).isEmpty();
    }

    @Test
    void tagCountUsesScard() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("tags:12")).thenReturn(3L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.tagCount(12L)).isEqualTo(3L);
        verify(setOps).size("tags:12");
    }

    @Test
    void commonTagsUsesSinter() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        // MacBook Pro (id=2) and LG Monitor (id=12) both have "pro-display"
        when(setOps.intersect("tags:2", "tags:12")).thenReturn(Set.of("pro-display"));

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.commonTags(2L, 12L)).containsExactly("pro-display");
        verify(setOps).intersect("tags:2", "tags:12");
    }

    @Test
    void commonTagsReturnsEmptySetWhenNoOverlap() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect("tags:3", "tags:12")).thenReturn(Set.of());

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.commonTags(3L, 12L)).isEmpty();
    }

    @Test
    void addTagIsIdempotentSaddDoesNotDoubleCount() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("tags:12", "sharp-4k-image")).thenReturn(1L, 0L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.addTag(12L, "sharp-4k-image")).isTrue();
        assertThat(service.addTag(12L, "sharp-4k-image")).isFalse();
    }

    @Test
    void rejectsInvalidInputs() {
        ProductTagService service = new ProductTagService(redisTemplate);

        assertThatThrownBy(() -> service.addTag(null, "tag"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productId cannot be null");
        assertThatThrownBy(() -> service.addTag(1L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag cannot be blank");
        assertThatThrownBy(() -> service.removeTag(1L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag cannot be blank");
        assertThatThrownBy(() -> service.commonTags(null, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productId cannot be null");
    }
}
