package com.substring.chat.config;

import com.substring.chat.services.MessageService;
import com.substring.chat.services.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class PresenceEventListener {
    private final PresenceService presence;
    private final SimpMessagingTemplate broker;

    public PresenceEventListener(PresenceService presence, SimpMessagingTemplate broker) {
        this.presence = presence;
        this.broker = broker;
    }

    @EventListener
    public void connected(SessionConnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        String roomId = first(headers, "roomId");
        String displayName = first(headers, "displayName");
        if (sessionId == null || roomId == null || displayName == null) return;

        try {
            MessageService.validRoom(roomId);
            presence.connect(sessionId, roomId, displayName);
            publish(roomId);
        } catch (IllegalArgumentException ignored) {
            // Invalid presence metadata must not bring down the WebSocket broker.
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        String roomId = presence.disconnect(event.getSessionId());
        if (roomId != null) publish(roomId);
    }

    private void publish(String roomId) {
        broker.convertAndSend("/topic/presence/" + roomId, presence.snapshot(roomId));
    }

    private static String first(StompHeaderAccessor headers, String name) {
        return headers.getNativeHeader(name) == null || headers.getNativeHeader(name).isEmpty()
                ? null : headers.getNativeHeader(name).get(0);
    }
}
