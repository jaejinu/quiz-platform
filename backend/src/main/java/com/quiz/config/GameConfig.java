package com.quiz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class GameConfig {

    @Bean(destroyMethod = "")
    public ScheduledExecutorService gameScheduler() {
        return Executors.newScheduledThreadPool(8, new CustomizableThreadFactory("game-timer-"));
    }
}
