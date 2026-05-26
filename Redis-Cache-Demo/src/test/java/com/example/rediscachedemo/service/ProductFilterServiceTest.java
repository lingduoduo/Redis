package com.example.rediscachedemo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ProductFilterServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;

    @Test
    void singleCriterionUsesSmembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("filter:brand:apple")).thenReturn(Set.of("1", "2", "3", "4", "5"));

        ProductFilterService service = new ProductFilterService(redisTemplate);

        assertThat(service.filter(Map.of("brand", "apple")))
                .containsExactlyInAnyOrder("1", "2", "3", "4", "5");
        verify(setOps).members("filter:brand:apple");
        verify(setOps, never()).intersect(anyCollection());
    }

    @Test
    void multipleCriteriaUseSinter() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect(anyCollection())).thenReturn(Set.of("1"));

        ProductFilterService service = new ProductFilterService(redisTemplate);

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("brand", "apple");
        criteria.put("os", "ios");

        assertThat(service.filter(criteria)).containsExactly("1");
        verify(setOps).intersect(anyCollection());
        verify(setOps, never()).members(any());
    }

    @Test
    void fullDemoFilterAppleIosOledScreen() {
        // iPhone 15 Pro (id=1): brand=apple, os=ios, screentype=oled, screensize=6.0-6.24
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect(anyCollection())).thenReturn(Set.of("1"));

        ProductFilterService service = new ProductFilterService(redisTemplate);

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("brand", "apple");
        criteria.put("os", "ios");
        criteria.put("screentype", "oled");
        criteria.put("screensize", "6.0-6.24");

        assertThat(service.filter(criteria)).containsExactly("1");
    }

    @Test
    void noMatchReturnsEmptySet() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect(anyCollection())).thenReturn(Set.of());

        ProductFilterService service = new ProductFilterService(redisTemplate);

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("brand", "apple");
        criteria.put("os", "android");  // no Apple product runs Android

        assertThat(service.filter(criteria)).isEmpty();
    }

    @Test
    void nullIntersectResultReturnsEmptySet() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect(anyCollection())).thenReturn(null);

        ProductFilterService service = new ProductFilterService(redisTemplate);

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("brand", "apple");
        criteria.put("os", "ios");

        assertThat(service.filter(criteria)).isEmpty();
    }

    @Test
    void nullSmembersResultReturnsEmptySet() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("filter:brand:apple")).thenReturn(null);

        ProductFilterService service = new ProductFilterService(redisTemplate);

        assertThat(service.filter(Map.of("brand", "apple"))).isEmpty();
    }

    @Test
    void filterKeyFormatIsCorrect() {
        ProductFilterService service = new ProductFilterService(redisTemplate);

        assertThat(service.filterKey("brand", "apple")).isEqualTo("filter:brand:apple");
        assertThat(service.filterKey("screensize", "6.0-6.24")).isEqualTo("filter:screensize:6.0-6.24");
    }

    @Test
    void rejectsNullCriteria() {
        ProductFilterService service = new ProductFilterService(redisTemplate);

        assertThatThrownBy(() -> service.filter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("criteria cannot be empty");
    }

    @Test
    void rejectsEmptyCriteria() {
        ProductFilterService service = new ProductFilterService(redisTemplate);

        assertThatThrownBy(() -> service.filter(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("criteria cannot be empty");
    }

    @Test
    void categoryFilterReturnsSamsungPhones() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.intersect(anyCollection())).thenReturn(Set.of("6"));

        ProductFilterService service = new ProductFilterService(redisTemplate);

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("brand", "samsung");
        criteria.put("category", "smartphone");

        assertThat(service.filter(criteria)).containsExactly("6");
    }
}
