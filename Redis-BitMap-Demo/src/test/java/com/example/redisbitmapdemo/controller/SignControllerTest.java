package com.example.redisbitmapdemo.controller;

import com.example.redisbitmapdemo.model.SignSummary;
import com.example.redisbitmapdemo.service.SignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignControllerTest {

    @Mock
    private SignService signService;

    @InjectMocks
    private SignController signController;

    @Test
    void signMarksRequestedDateAndReturnsBitmapSummary() {
        LocalDate date = LocalDate.of(2026, 4, 20);
        when(signService.hasSigned(42L, date)).thenReturn(true);
        when(signService.signDaysOfMonth(42L, date)).thenReturn(7L);
        when(signService.currentStreak(42L, date)).thenReturn(3L);

        Map<String, Object> result = signController.sign(42L, date);

        verify(signService).sign(42L, date);
        assertThat(result)
                .containsEntry("message", "sign success")
                .containsEntry("userId", 42L)
                .containsEntry("date", date)
                .containsEntry("signed", true)
                .containsEntry("signedDaysOfMonth", 7L)
                .containsEntry("currentStreak", 3L);
    }

    @Test
    void summaryDelegatesWithRequestedDate() {
        LocalDate date = LocalDate.of(2026, 4, 20);
        SignSummary summary = new SignSummary(42L, "202604", true, 7L, 3L);
        when(signService.summary(42L, date)).thenReturn(summary);

        assertThat(signController.summary(42L, date)).isEqualTo(summary);
    }
}
