package com.substring.chat;

import com.substring.chat.controllers.ApiExceptionHandler;
import com.substring.chat.controllers.PresenceController;
import com.substring.chat.controllers.RoomController;
import com.substring.chat.entities.Room;
import com.substring.chat.repositories.RoomRepository;
import com.substring.chat.services.MessageService;
import com.substring.chat.services.PresenceService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomApiTest {
    @Test
    void roomResponsesHideDatabaseInternals() throws Exception {
        var rooms = mock(RoomRepository.class);
        var messages = mock(MessageService.class);
        when(rooms.insert(any(Room.class))).thenAnswer(call -> call.getArgument(0));
        var room = new Room();
        room.setRoomId("demo");
        room.setId("internal-mongo-id");
        room.setNextSequence(30);
        when(rooms.findByRoomId("demo")).thenReturn(Optional.of(room));
        var mvc = MockMvcBuilders.standaloneSetup(new RoomController(rooms, messages))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/v1/rooms").contentType("text/plain").content("demo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value("demo"))
                .andExpect(jsonPath("$.id").doesNotExist());
        mvc.perform(get("/api/v1/rooms/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("demo"))
                .andExpect(jsonPath("$.nextSequence").doesNotExist());
    }

    @Test
    void missingRoomAndPresenceReturn404() throws Exception {
        var rooms = mock(RoomRepository.class);
        var messages = mock(MessageService.class);
        when(rooms.findByRoomId("missing")).thenReturn(Optional.empty());
        when(rooms.existsByRoomId("missing")).thenReturn(false);
        var mvc = MockMvcBuilders.standaloneSetup(
                new RoomController(rooms, messages),
                new PresenceController(new PresenceService(), rooms))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/rooms/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Room not found"));
        mvc.perform(get("/api/v1/rooms/missing/presence"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Room not found"));
    }
}
