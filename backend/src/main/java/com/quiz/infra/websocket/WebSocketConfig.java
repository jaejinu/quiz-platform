package com.quiz.infra.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> allowedOrigins = properties.allowedOrigins();
        if (allowedOrigins.isEmpty()) {
            log.warn("quiz.websocket.allowed-origins is empty; STOMP endpoint '{}' will reject all cross-origin handshakes. "
                    + "Configure it in application-<profile>.yml.", properties.endpoint());
        }
        registry.addEndpoint(properties.endpoint())
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
