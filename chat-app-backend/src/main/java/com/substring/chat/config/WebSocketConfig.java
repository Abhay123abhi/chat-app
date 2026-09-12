package com.substring.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
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
        // Same-origin only. Vite and nginx proxy /chat to the backend.
        registry.addEndpoint("/chat").withSockJS();
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
                if (headers.getCommand() == StompCommand.SEND) {
                    throw new MessageDeliveryException("Send messages through the durable REST endpoint");
                }
                if (headers.getCommand() == StompCommand.SUBSCRIBE) {
                    String destination = headers.getDestination();
                    boolean messageTopic = destination != null
                            && destination.matches("/topic/room/[A-Za-z0-9_-]{1,64}");
                    boolean presenceTopic = destination != null
                            && destination.matches("/topic/presence/[A-Za-z0-9_-]{1,64}");
                    if (!messageTopic && !presenceTopic) {
                        throw new MessageDeliveryException("Invalid room subscription");
                    }
                }
                return message;
            }
        });
    }
}
