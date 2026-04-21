package com.example.redismqdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RedisMqDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisMqDemoApplication.class, args);
    }
}
