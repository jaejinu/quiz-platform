package com.quiz.config;

import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceIdConfig {

    @Bean
    @Qualifier("publisherId")
    public String publisherId(@Value("${quiz.instance.id:}") String overrideId) {
        if (overrideId != null && !overrideId.isBlank()) {
            return overrideId;
        }
        String generated = UUID.randomUUID().toString();
        LoggerFactory.getLogger(InstanceIdConfig.class).info("Instance publisherId={}", generated);
        return generated;
    }
}
