package com.chainreaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
public class ChainStoriesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChainStoriesApplication.class, args);
    }
}
