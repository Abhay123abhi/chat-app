package com.substring.chat.controllers;

import com.substring.chat.dto.MessagePageResponse;
import com.substring.chat.entities.Room;
import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.services.MessageService;
import com.substring.chat.validation.ChatValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomRepository rooms;
    private final MessageService messages;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room create(@RequestBody String roomId) {
        ChatValidation.validRoomId(roomId);

        var room = new Room();
        room.setRoomId(roomId);

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
    public MessagePageResponse history(
            @PathVariable String roomId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "50") int limit) {
        return messages.history(roomId, before, after, limit);
    }
}
