package com.substring.chat.config;

import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.security.ClientIpResolver;
import com.substring.chat.services.PresenceService;
import com.substring.chat.validation.ChatValidation;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String TOPIC_PREFIX = "/topic";
    private static final String APPLICATION_PREFIX = "/app";
    private static final String CHAT_ENDPOINT = "/ws-chat";
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final int MESSAGE_SIZE_LIMIT_BYTES = 8_192;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 65_536;
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private final ChatProperties properties;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;
    private final PresenceService presence;
    private final RoomRepository rooms;

    @Bean
    public ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker(TOPIC_PREFIX)
                .setHeartbeatValue(new long[] {HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(chatHeartbeatScheduler());
        config.setApplicationDestinationPrefixes(APPLICATION_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(CHAT_ENDPOINT)
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        attributes.put("clientIp", clientIpResolver.resolve(request));
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES)
                .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT_BYTES)
                .setSendTimeLimit(SEND_TIME_LIMIT_MS);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var headers = StompHeaderAccessor.wrap(message);
                if (headers.getCommand() == StompCommand.CONNECT) admitConnection(headers);
                if (headers.getCommand() == StompCommand.SEND) {
                    throw new MessageDeliveryException("Send messages through the durable REST endpoint");
                }
                if (headers.getCommand() == StompCommand.SUBSCRIBE) {
                    var destination = headers.getDestination();
                    var roomId = presence.roomForSession(headers.getSessionId());
                    var messageTopic = roomId != null && ("/topic/room/" + roomId).equals(destination);
                    var presenceTopic = roomId != null && ("/topic/presence/" + roomId).equals(destination);
                    if (!messageTopic && !presenceTopic) {
                        throw new MessageDeliveryException("Invalid room subscription");
                    }
                }
                return message;
            }
        });
    }

    private void admitConnection(StompHeaderAccessor headers) {
        if (!properties.isEnabled()) throw new MessageDeliveryException("Chat is temporarily disabled");

        var sessionId = headers.getSessionId();
        var roomId = headers.getFirstNativeHeader("roomId");
        var displayName = headers.getFirstNativeHeader("displayName");
        if (sessionId == null || roomId == null || displayName == null) {
            throw new MessageDeliveryException("Room and display name are required");
        }

        try {
            ChatValidation.validRoomId(roomId);
        } catch (RuntimeException invalidRoom) {
            throw new MessageDeliveryException("Invalid room");
        }

        if (!rooms.existsByRoomId(roomId)) throw new MessageDeliveryException("Room not found");

        var sessionAttributes = headers.getSessionAttributes();
        var clientIp = sessionAttributes == null
                ? "unknown"
                : String.valueOf(sessionAttributes.getOrDefault("clientIp", "unknown"));

        if (!protection.tryOpenWebSocket(sessionId, clientIp, roomId)) {
            throw new MessageDeliveryException("WebSocket connection limit reached");
        }

        try {
            presence.connect(sessionId, roomId, displayName);
        } catch (RuntimeException rejected) {
            protection.closeWebSocket(sessionId);
            throw new MessageDeliveryException(rejected.getMessage() == null
                    ? "WebSocket connection rejected"
                    : rejected.getMessage());
        }
    }
}
