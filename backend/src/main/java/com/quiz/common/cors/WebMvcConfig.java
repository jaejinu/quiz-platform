package com.quiz.common.cors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 필터 체인 앞단에서 적용되도록 CorsConfigurationSource 빈을 제공.
 * SecurityConfig의 cors(Customizer.withDefaults())가 이 빈을 자동 픽업.
 *
 * WebMvcConfigurer.addCorsMappings는 Security 뒤 MVC 레이어에서만 적용되므로
 * preflight(OPTIONS)가 Security에 막힐 수 있어 이 방식을 채택.
 */
@Configuration
public class WebMvcConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${quiz.websocket.allowed-origins:}") List<String> allowedOrigins) {

        List<String> origins = (allowedOrigins == null || allowedOrigins.isEmpty())
            ? List.of("http://localhost:5173", "http://localhost:3000")
            : allowedOrigins;

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/ws/**", config);
        return source;
    }
}
