package com.example.redishashdemo.controller;

import com.example.redishashdemo.model.CartSummary;
import com.example.redishashdemo.service.CartHashService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CartControllerTest {

    private final CartHashService cartHashService = mock(CartHashService.class);
    private final CartController controller = new CartController(cartHashService);

    @Test
    void addOneReturnsUpdatedQuantity() {
        when(cartHashService.addOne("u42", "p1001")).thenReturn(2L);

        assertThat(controller.addOne("u42", "p1001"))
                .containsEntry("userId", "u42")
                .containsEntry("productId", "p1001")
                .containsEntry("quantity", 2L);
    }

    @Test
    void removeOneReturnsUpdatedQuantity() {
        when(cartHashService.removeOne("u42", "p1001")).thenReturn(1L);

        assertThat(controller.removeOne("u42", "p1001"))
                .containsEntry("quantity", 1L);
    }

    @Test
    void deleteProductDelegatesToService() {
        assertThat(controller.deleteProduct("u42", "p1001"))
                .containsEntry("message", "product removed")
                .containsEntry("userId", "u42")
                .containsEntry("productId", "p1001");

        verify(cartHashService).deleteProduct("u42", "p1001");
    }

    @Test
    void cartReturnsSummary() {
        CartSummary summary = new CartSummary("u42", "cart:u42", 0L, List.of());
        when(cartHashService.summary("u42")).thenReturn(summary);

        assertThat(controller.cart("u42")).isEqualTo(summary);
    }

    @Test
    void productKindsReturnsHashLength() {
        when(cartHashService.productKinds("u42")).thenReturn(3L);

        assertThat(controller.productKinds("u42"))
                .containsEntry("userId", "u42")
                .containsEntry("productKinds", 3L);
    }
}
