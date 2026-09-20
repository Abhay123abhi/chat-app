package com.substring.chat.config;

import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.services.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceEventListener {
    private final PresenceService presence;
    private final SimpMessagingTemplate broker;
    private final AbuseProtectionService protection;

    public PresenceEventListener(PresenceService presence, SimpMessagingTemplate broker,
            AbuseProtectionService protection) {
        this.presence = presence;
        this.broker = broker;
        this.protection = protection;
    }

    @EventListener
    public void connected(SessionConnectEvent event) {
        String roomId = presence.roomForSession(event.getMessage().getHeaders().get("simpSessionId", String.class));
        if (roomId != null) publish(roomId);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String roomId = presence.disconnect(sessionId);
        protection.closeWebSocket(sessionId);
        if (roomId != null) publish(roomId);
    }

    private void publish(String roomId) {
        broker.convertAndSend("/topic/presence/" + roomId, presence.snapshot(roomId));
    }
}
