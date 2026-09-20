package com.substring.chat.controllers;

import com.substring.chat.entities.Message;
import com.substring.chat.playload.MessageRequest;
import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.security.ClientIpResolver;
import com.substring.chat.services.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/messages")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final MessageService messages;
    private final SimpMessagingTemplate broker;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;

    public ChatController(MessageService messages, SimpMessagingTemplate broker,
            AbuseProtectionService protection, ClientIpResolver clientIpResolver) {
        this.messages = messages;
        this.broker = broker;
        this.protection = protection;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping
    public Message send(@PathVariable String roomId, @RequestBody MessageRequest request,
            HttpServletRequest servletRequest) {
        String clientId = clientIpResolver.resolve(servletRequest);
        if (!protection.allowMessageBurst(clientId) || !protection.allowMessage(clientId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Message rate limit exceeded; retry shortly");
        }

        Message saved = messages.save(roomId, request);
        try {
            broker.convertAndSend("/topic/room/" + saved.getRoomId(), saved);
        } catch (RuntimeException failure) {
            log.warn("Live notification failed for message {}", saved.getId(), failure);
        }
        return saved;
    }
}
