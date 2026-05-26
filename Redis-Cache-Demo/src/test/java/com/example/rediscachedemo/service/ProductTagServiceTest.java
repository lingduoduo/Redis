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
        when(setOps.add("tags:12", "画面清晰细腻")).thenReturn(1L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.addTag(12L, "画面清晰细腻")).isTrue();
        verify(setOps).add("tags:12", "画面清晰细腻");
    }

    @Test
    void addTagReturnsFalseForDuplicateTag() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("tags:12", "画面清晰细腻")).thenReturn(0L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.addTag(12L, "画面清晰细腻")).isFalse();
    }

    @Test
    void removeTagUsesSremAndReturnsTrueWhenPresent() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("tags:12", (Object) "画面清晰细腻")).thenReturn(1L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.removeTag(12L, "画面清晰细腻")).isTrue();
        verify(setOps).remove("tags:12", (Object) "画面清晰细腻");
    }

    @Test
    void removeTagReturnsFalseWhenTagAbsent() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.remove("tags:12", (Object) "不存在标签")).thenReturn(0L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.removeTag(12L, "不存在标签")).isFalse();
    }

    @Test
    void hasTagUsesSismember() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("tags:12", "真彩清晰显示屏")).thenReturn(true);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.hasTag(12L, "真彩清晰显示屏")).isTrue();
        verify(setOps).isMember("tags:12", "真彩清晰显示屏");
    }

    @Test
    void tagsUsesSmembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("tags:12"))
                .thenReturn(Set.of("画面清晰细腻", "真彩清晰显示屏", "流畅至极"));

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.tags(12L))
                .containsExactlyInAnyOrder("画面清晰细腻", "真彩清晰显示屏", "流畅至极");
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
        // MacBook Pro (id=2) and LG Monitor (id=12) both have "专业显示屏"
        when(setOps.intersect("tags:2", "tags:12")).thenReturn(Set.of("专业显示屏"));

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.commonTags(2L, 12L)).containsExactly("专业显示屏");
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
        when(setOps.add("tags:12", "画面清晰细腻")).thenReturn(1L, 0L);

        ProductTagService service = new ProductTagService(redisTemplate);

        assertThat(service.addTag(12L, "画面清晰细腻")).isTrue();
        assertThat(service.addTag(12L, "画面清晰细腻")).isFalse();
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
