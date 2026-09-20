package com.substring.chat.config;

import com.substring.chat.services.PresenceService;
import com.substring.chat.validation.ChatValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PresenceEventListener {
    private final PresenceService presence;
    private final SimpMessagingTemplate broker;

    @EventListener
    public void connected(SessionConnectEvent event) {
        var headers = StompHeaderAccessor.wrap(event.getMessage());
        var sessionId = headers.getSessionId();
        var roomId = headers.getFirstNativeHeader("roomId");
        var displayName = headers.getFirstNativeHeader("displayName");

        if (sessionId == null || roomId == null || displayName == null) {
            return;
        }

        try {
            ChatValidation.validRoomId(roomId);
            presence.connect(sessionId, roomId, displayName);
            publish(roomId);
        } catch (IllegalArgumentException | ResponseStatusException ignored) {
            // Invalid presence metadata must not bring down the WebSocket broker.
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        var roomId = presence.disconnect(event.getSessionId());
        if (roomId != null) {
            publish(roomId);
        }
    }

    private void publish(String roomId) {
        broker.convertAndSend("/topic/presence/" + roomId, presence.snapshot(roomId));
    }
}
