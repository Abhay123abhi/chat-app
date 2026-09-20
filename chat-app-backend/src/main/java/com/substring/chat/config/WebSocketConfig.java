package com.substring.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String TOPIC_PREFIX = "/topic";
    private static final String APPLICATION_PREFIX = "/app";
    private static final String CHAT_ENDPOINT = "/chat";
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final int MESSAGE_SIZE_LIMIT_BYTES = 8_192;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 65_536;
    private static final int SEND_TIME_LIMIT_MS = 10_000;

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
        registry.addEndpoint(CHAT_ENDPOINT).withSockJS();
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

                if (headers.getCommand() == StompCommand.SEND) {
                    throw new MessageDeliveryException("Send messages through the durable REST endpoint");
                }

                if (headers.getCommand() == StompCommand.SUBSCRIBE && !isAllowedSubscription(headers.getDestination())) {
                    throw new MessageDeliveryException("Invalid room subscription");
                }

                return message;
            }
        });
    }

    private static boolean isAllowedSubscription(String destination) {
        return destination != null
                && destination.matches("/topic/(room|presence)/[A-Za-z0-9_-]{1,64}");
    }
}
