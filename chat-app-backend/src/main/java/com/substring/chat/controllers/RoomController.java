package com.substring.chat.controllers;

import com.substring.chat.entities.Room;
import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.services.MessageService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomRepository rooms;
    private final MessageService messages;

    public RoomController(RoomRepository rooms, MessageService messages) {
        this.rooms = rooms;
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room create(@RequestBody String roomId) {
        MessageService.validRoom(roomId);
        Room room = new Room();
        room.setRoomId(roomId);
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
            @RequestParam(defaultValue = "50") int limit) {
        return messages.history(roomId, before, after, limit);
    }
}
