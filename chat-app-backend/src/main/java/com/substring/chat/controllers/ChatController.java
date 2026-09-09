package com.substring.chat.controllers;

import com.substring.chat.entities.Message;
import com.substring.chat.playload.MessageRequest;
import com.substring.chat.services.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/messages")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final MessageService messages;
    private final SimpMessagingTemplate broker;

    public ChatController(MessageService messages, SimpMessagingTemplate broker) {
        this.messages = messages;
        this.broker = broker;
    }

    @PostMapping
    public Message send(@PathVariable String roomId, @RequestBody MessageRequest request) {
        Message saved = messages.save(roomId, request);
        try {
            broker.convertAndSend("/topic/room/" + saved.getRoomId(), saved);
        } catch (RuntimeException failure) {
            // A failed notification must not turn a successful durable write into a failed send.
            log.warn("Live notification failed for message {}", saved.getId(), failure);
        }
        return saved;
    }
}
