package com.substring.chat.controllers;

import com.substring.chat.dto.PresenceSnapshot;
import com.substring.chat.services.PresenceService;
import com.substring.chat.validation.ChatValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms/{roomId}/presence")
public class PresenceController {
    private final PresenceService presence;

    @GetMapping
    public PresenceSnapshot snapshot(@PathVariable String roomId) {
        ChatValidation.validRoomId(roomId);
        return presence.snapshot(roomId);
    }
}
