package com.substring.chat.controllers;

import com.substring.chat.config.ChatProperties;
import com.substring.chat.dto.MessagePageResponse;
import com.substring.chat.entities.Room;
import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.security.AbuseProtectionService;
import com.substring.chat.security.ClientIpResolver;
import com.substring.chat.services.MessageService;
import com.substring.chat.validation.ChatValidation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomRepository rooms;
    private final MessageService messages;
    private final ChatProperties properties;
    private final AbuseProtectionService protection;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room create(@RequestBody String roomId, HttpServletRequest request) {
        if (!properties.isRoomCreationEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Room creation is temporarily disabled");
        }

        var clientId = clientIpResolver.resolve(request);
        if (!protection.allowRoomCreation(clientId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Room creation rate limit reached; retry later");
        }

        var normalized = roomId == null ? null : roomId.trim();
        ChatValidation.validRoomId(normalized);

        if (rooms.count() >= Math.max(1, properties.getMaxActiveRooms())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Public demo room capacity reached; try again later");
        }

        var now = Instant.now();
        var room = new Room();
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
        ChatValidation.validRoomId(roomId);
        return rooms.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    @GetMapping("/{roomId}/messages")
    public MessagePageResponse history(@PathVariable String roomId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        var pageSize = limit == null ? properties.getDefaultHistoryPageSize() : limit;
        return messages.history(roomId, before, after, pageSize);
    }
}
