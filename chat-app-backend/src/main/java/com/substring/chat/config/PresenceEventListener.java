package com.substring.chat.config;

import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PresenceEventListener {
    private final PresenceService presence;
    private final SimpMessagingTemplate broker;
    private final AbuseProtectionService protection;

    @EventListener
    public void connected(SessionConnectEvent event) {
        var headers = StompHeaderAccessor.wrap(event.getMessage());
        var roomId = presence.roomForSession(headers.getSessionId());
        if (roomId != null) publish(roomId);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        var sessionId = event.getSessionId();
        var roomId = presence.disconnect(sessionId);
        protection.closeWebSocket(sessionId);
        if (roomId != null) publish(roomId);
    }

    private void publish(String roomId) {
        broker.convertAndSend("/topic/presence/" + roomId, presence.snapshot(roomId));
    }
}
