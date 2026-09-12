package com.substring.chat.controllers;

import com.substring.chat.services.MessageService;
import com.substring.chat.services.PresenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/presence")
public class PresenceController {
    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @GetMapping
    public PresenceService.PresenceSnapshot snapshot(@PathVariable String roomId) {
        MessageService.validRoom(roomId);
        return presence.snapshot(roomId);
    }
}
