package com.example.redisrankdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RedisRankDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisRankDemoApplication.class, args);
    }
}
