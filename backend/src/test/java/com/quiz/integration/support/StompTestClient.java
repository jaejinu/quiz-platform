package com.quiz.integration.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 통합 테스트에서 STOMP over SockJS 연결/구독/발행을 돕는 헬퍼.
 *
 * CONNECT 프레임의 Authorization 헤더는 {@code StubAuthTokenResolver} 포맷
 * {@code "Bearer stub:<userId>:<nickname>:<role>"} 를 따른다.
 */
@Slf4j
@Component
@Profile("test")
public class StompTestClient {

    /**
     * SockJS handshake는 HTTP URL을 요구한다. WebSocketConfig가 {@code /ws} 에
     * {@code withSockJS()} 로 endpoint를 열어 뒀으므로 그대로 쓴다.
     */
    public StompSession connect(int port, long userId, String nickname, String role) throws Exception {
        String url = "http://localhost:" + port + "/ws";

        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        client.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer stub:" + userId + ":" + nickname + ":" + role);

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();

        return client.connectAsync(url, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    /**
     * destination을 구독하고 수신 프레임을 블로킹 큐로 흘려보낸다.
     * poll(timeout) 으로 이벤트 도착을 기다릴 수 있다.
     */
    public <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload == null) {
                    return;
                }
                queue.offer(type.cast(payload));
            }
        });
        return queue;
    }

    public void send(StompSession session, String destination, Object payload) {
        if (payload == null) {
            session.send(destination, new byte[0]);
        } else {
            session.send(destination, payload);
        }
    }
}
