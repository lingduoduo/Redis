package com.example.redishashdemo.service;

import com.example.redishashdemo.model.CartItem;
import com.example.redishashdemo.model.CartSummary;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class CartHashServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
    private final CartHashService service = new CartHashService(redisTemplate);

    @Test
    void addOneUsesHincrBy() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment("cart:u42", "p1001", 1L)).thenReturn(3L);

        assertThat(service.addOne("u42", "p1001")).isEqualTo(3L);

        verify(hashOps).increment("cart:u42", "p1001", 1L);
    }

    @Test
    void removeOneUsesNegativeHincrBy() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment("cart:u42", "p1001", -1L)).thenReturn(2L);

        assertThat(service.removeOne("u42", "p1001")).isEqualTo(2L);

        verify(hashOps).increment("cart:u42", "p1001", -1L);
    }

    @Test
    void decrementDeletesFieldWhenQuantityReachesZero() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment("cart:u42", "p1001", -1L)).thenReturn(0L);

        assertThat(service.removeOne("u42", "p1001")).isZero();

        verify(hashOps).delete("cart:u42", "p1001");
    }

    @Test
    void deleteProductUsesHdel() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        service.deleteProduct("u42", "p1001");

        verify(hashOps).delete("cart:u42", "p1001");
    }

    @Test
    void entriesUsesHgetallAndProductKindsUsesHlen() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        Map<Object, Object> entries = Map.of("p1001", "2");
        when(hashOps.entries("cart:u42")).thenReturn(entries);
        when(hashOps.size("cart:u42")).thenReturn(1L);

        assertThat(service.entries("u42")).isEqualTo(entries);
        assertThat(service.productKinds("u42")).isEqualTo(1L);

        verify(hashOps).entries("cart:u42");
        verify(hashOps).size("cart:u42");
    }

    @Test
    void summaryReturnsSortedPositiveItems() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("p2", "3");
        entries.put("p1", "2");
        entries.put("p0", "bad");
        when(hashOps.entries("cart:u42")).thenReturn(entries);
        when(hashOps.size("cart:u42")).thenReturn(3L);

        CartSummary summary = service.summary("u42");

        assertThat(summary).isEqualTo(new CartSummary(
                "u42",
                "cart:u42",
                3L,
                List.of(new CartItem("p1", 2L), new CartItem("p2", 3L))
        ));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> service.addOne("", "p1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be blank");
        assertThatThrownBy(() -> service.addOne("u42", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productId cannot be blank");
        assertThatThrownBy(() -> service.increment("u42", "p1001", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delta cannot be zero");
    }
}
