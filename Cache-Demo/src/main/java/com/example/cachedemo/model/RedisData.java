package com.example.cachedemo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisData<T> {
    private T data;
    private Long expireTime; // epoch millis
}
