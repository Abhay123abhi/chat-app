package com.substring.chat.controllers;

import com.substring.chat.config.ChatProperties;
import com.substring.chat.entities.Room;
import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.security.ClientIpResolver;
import com.substring.chat.services.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomRepository rooms;
    private final MessageService messages;
    private final ChatProperties properties;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;

    public RoomController(RoomRepository rooms, MessageService messages, ChatProperties properties,
            AbuseProtectionService protection, ClientIpResolver clientIpResolver) {
        this.rooms = rooms;
        this.messages = messages;
        this.properties = properties;
        this.protection = protection;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room create(@RequestBody String roomId, HttpServletRequest request) {
        if (!properties.isRoomCreationEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Room creation is temporarily disabled");
        }
        String clientId = clientIpResolver.resolve(request);
        if (!protection.allowRoomCreation(clientId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Room creation limit reached; retry later");
        }

        String normalized = roomId == null ? null : roomId.trim();
        MessageService.validRoom(normalized);
        Instant now = Instant.now();
        Room room = new Room();
        room.setRoomId(normalized);
        room.setCreatedAt(now);
        room.setLastActivityAt(now);
        room.setExpiresAt(now.plus(Math.max(1, properties.getRoomInactivityDays()), ChronoUnit.DAYS));
        try {
            return rooms.insert(room);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room already exists");
        }
    }

    @GetMapping("/{roomId}")
    public Room join(@PathVariable String roomId) {
        MessageService.validRoom(roomId);
        Room room = rooms.findByRoomId(roomId);
        if (room == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        return room;
    }

    @GetMapping("/{roomId}/messages")
    public MessageService.MessagePage history(@PathVariable String roomId,
            @RequestParam(required = false) Long before, @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        int pageSize = limit == null ? properties.getDefaultHistoryPageSize() : limit;
        return messages.history(roomId, before, after, pageSize);
    }
}
