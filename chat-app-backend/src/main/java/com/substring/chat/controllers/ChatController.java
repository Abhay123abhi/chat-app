package com.substring.chat.controllers;

import com.substring.chat.dto.MessageRequest;
import com.substring.chat.entities.Message;
import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.security.ClientIpResolver;
import com.substring.chat.services.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms/{roomId}/messages")
public class ChatController {
    private final MessageService messages;
    private final SimpMessagingTemplate broker;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public Message send(@PathVariable String roomId, @Valid @RequestBody MessageRequest request,
            HttpServletRequest servletRequest) {
        var clientId = clientIpResolver.resolve(servletRequest);
        if (!protection.allowMessageBurst(clientId) || !protection.allowMessage(clientId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Message rate limit reached; retry shortly");
        }

        var saved = messages.save(roomId, request);
        try {
            broker.convertAndSend("/topic/room/" + saved.getRoomId(), saved);
        } catch (RuntimeException failure) {
            log.warn("Live notification failed for message {}", saved.getId(), failure);
        }
        return saved;
    }
}
