package com.example.redisbitmapdemo.model;

import java.time.LocalDate;

public record RetentionStats(
        LocalDate baseDate,
        LocalDate retainedDate,
        long baseUsers,
        long retainedUsers
) {
}
