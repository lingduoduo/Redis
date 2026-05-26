package com.example.redisbitmapdemo.controller;

import com.example.redisbitmapdemo.model.ActivityStats;
import com.example.redisbitmapdemo.model.RetentionStats;
import com.example.redisbitmapdemo.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserActivityControllerTest {

    private UserActivityService userActivityService;
    private UserActivityController controller;

    @BeforeEach
    void setUp() {
        userActivityService = mock(UserActivityService.class);
        controller = new UserActivityController(userActivityService);
    }

    @Test
    void markOnlineRecordsRequestedDateAndReturnsStatus() {
        LocalDate date = LocalDate.of(2026, 5, 26);
        when(userActivityService.wasOnline(42L, date)).thenReturn(true);

        Map<String, Object> result = controller.markOnline(42L, date);

        verify(userActivityService).markOnline(42L, date);
        assertThat(result)
                .containsEntry("message", "online marked")
                .containsEntry("userId", 42L)
                .containsEntry("date", date)
                .containsEntry("online", true);
    }

    @Test
    void dailyActiveUsersReturnsBitCountResult() {
        LocalDate date = LocalDate.of(2026, 5, 26);
        when(userActivityService.dailyActiveUsers(date)).thenReturn(12L);

        assertThat(controller.dailyActiveUsers(date))
                .containsEntry("date", date)
                .containsEntry("activeUsers", 12L);
    }

    @Test
    void activityStatsDelegatesToService() {
        LocalDate start = LocalDate.of(2026, 5, 20);
        ActivityStats stats = new ActivityStats(start, LocalDate.of(2026, 5, 26), 3L, 10L);
        when(userActivityService.activityStats(start, 7)).thenReturn(stats);

        assertThat(controller.activityStats(start, 7)).isEqualTo(stats);
    }

    @Test
    void retentionDelegatesToService() {
        LocalDate base = LocalDate.of(2026, 5, 20);
        LocalDate retained = LocalDate.of(2026, 5, 26);
        RetentionStats stats = new RetentionStats(base, retained, 20L, 4L);
        when(userActivityService.retention(base, retained)).thenReturn(stats);

        assertThat(controller.retention(base, retained)).isEqualTo(stats);
    }
}
