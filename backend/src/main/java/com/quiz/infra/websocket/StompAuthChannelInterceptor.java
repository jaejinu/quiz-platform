package com.quiz.infra.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final AuthTokenResolver authTokenResolver;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader(AUTH_HEADER);
        Optional<AuthPrincipal> principal = authTokenResolver.resolve(authorization);
        if (principal.isEmpty()) {
            log.warn("STOMP CONNECT rejected: missing or invalid Authorization header");
            throw new MessageDeliveryException("Unauthorized");
        }

        AuthPrincipal auth = principal.get();
        accessor.setUser(auth);
        log.info("STOMP CONNECT authenticated: userId={}, nickname={}, role={}",
                auth.userId(), auth.nickname(), auth.role());
        return message;
    }
}
