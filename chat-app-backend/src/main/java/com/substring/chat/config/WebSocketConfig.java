package com.substring.chat.config;

import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.security.ClientIpResolver;
import com.substring.chat.services.MessageService;
import com.substring.chat.services.PresenceService;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatProperties properties;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;
    private final PresenceService presence;
    private final RoomRepository rooms;

    public WebSocketConfig(ChatProperties properties, AbuseProtectionService protection,
            ClientIpResolver clientIpResolver, PresenceService presence, RoomRepository rooms) {
        this.properties = properties;
        this.protection = protection;
        this.clientIpResolver = clientIpResolver;
        this.presence = presence;
        this.rooms = rooms;
    }

    @Bean
    public ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {10000, 10000})
                .setTaskScheduler(chatHeartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat")
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
        registration.setMessageSizeLimit(8192).setSendBufferSizeLimit(65536).setSendTimeLimit(10000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);

                if (headers.getCommand() == StompCommand.CONNECT) {
                    admitConnection(headers);
                }

                if (headers.getCommand() == StompCommand.SEND) {
                    throw new MessageDeliveryException("Send messages through the durable REST endpoint");
                }

                if (headers.getCommand() == StompCommand.SUBSCRIBE) {
                    String destination = headers.getDestination();
                    String roomId = presence.roomForSession(headers.getSessionId());
                    boolean messageTopic = roomId != null && ("/topic/room/" + roomId).equals(destination);
                    boolean presenceTopic = roomId != null && ("/topic/presence/" + roomId).equals(destination);
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

        String sessionId = headers.getSessionId();
        String roomId = first(headers, "roomId");
        String displayName = first(headers, "displayName");
        if (sessionId == null || roomId == null || displayName == null) {
            throw new MessageDeliveryException("Room and display name are required");
        }

        MessageService.validRoom(roomId);
        if (!rooms.existsByRoomId(roomId)) throw new MessageDeliveryException("Room not found");

        Map<String, Object> sessionAttributes = headers.getSessionAttributes();
        String clientIp = sessionAttributes == null ? "unknown"
                : String.valueOf(sessionAttributes.getOrDefault("clientIp", "unknown"));

        if (!protection.tryOpenWebSocket(sessionId, clientIp)) {
            throw new MessageDeliveryException("Too many WebSocket connections from this client");
        }

        try {
            presence.connect(sessionId, roomId, displayName);
        } catch (RuntimeException failure) {
            protection.closeWebSocket(sessionId);
            throw new MessageDeliveryException(failure.getMessage() == null ? "WebSocket connection rejected" : failure.getMessage());
        }
    }

    private static String first(StompHeaderAccessor headers, String name) {
        return headers.getNativeHeader(name) == null || headers.getNativeHeader(name).isEmpty()
                ? null : headers.getNativeHeader(name).get(0);
    }
}
