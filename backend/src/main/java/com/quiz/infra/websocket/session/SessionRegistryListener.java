package com.quiz.infra.websocket.session;

import com.quiz.domain.participant.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRegistryListener {

    private final ParticipantService participantService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("STOMP connected sessionId={} user={}", accessor.getSessionId(), accessor.getUser());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        log.debug("STOMP disconnect sessionId={}", sessionId);
        try {
            participantService.disconnect(sessionId);
        } catch (Exception e) {
            log.warn("disconnect handling failed sessionId={}: {}", sessionId, e.getMessage(), e);
        }
    }
}
