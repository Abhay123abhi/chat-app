package com.substring.chat;

import com.substring.chat.entities.Message;
import com.substring.chat.entities.Room;
import com.substring.chat.playload.MessageRequest;
import com.substring.chat.services.MessageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatAppBackendApplicationTests {
    private MongoTemplate mongo;
    private MessageService service;

    @BeforeEach
    void setup() {
        mongo = mock(MongoTemplate.class);
        service = new MessageService(mongo);
    }

    @Test
    void retryReturnsOriginalWithoutAllocatingOrInsertingAgain() {
        Message original = message(4);
        original.setSender("Abhay");
        original.setContent("hello");
        when(mongo.findOne(any(Query.class), eq(Message.class))).thenReturn(original);
        assertSame(original, service.save("room", new MessageRequest("retry-key", "Abhay", "hello")));
        verify(mongo, never()).insert(any(Message.class));
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class));
    }

    @Test
    void reusedRequestIdCannotChangeContent() {
        Message original = message(4);
        original.setSender("Abhay");
        original.setContent("first");
        when(mongo.findOne(any(Query.class), eq(Message.class))).thenReturn(original);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.save("room", new MessageRequest("same-key", "Abhay", "different"))).getStatusCode().value());
    }

    @Test
    void invalidInputDoesNotTouchDatabase() {
        assertThrows(ResponseStatusException.class, () -> service.save("bad/room", new MessageRequest("key", "A", "hello")));
        assertThrows(ResponseStatusException.class, () -> service.save("room", new MessageRequest("key", "A", " ")));
        assertThrows(ResponseStatusException.class, () -> service.history("room", 5L, 2L, 50));
        assertThrows(ResponseStatusException.class, () -> service.history("room", null, null, 101));
        verifyNoInteractions(mongo);
    }

    @Test
    void olderPageReturnsChronologicalResultsAndExclusiveCursor() {
        when(mongo.exists(any(Query.class), eq(Room.class))).thenReturn(true);
        when(mongo.find(any(Query.class), eq(Message.class))).thenReturn(List.of(message(10), message(9), message(8)));
        var page = service.history("room", 11L, null, 2);
        assertEquals(List.of(9L, 10L), page.messages().stream().map(Message::getSequence).toList());
        assertTrue(page.hasMore());
        assertEquals(9L, page.nextCursor());
        var captor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(Message.class));
        assertEquals(3, captor.getValue().getLimit());
        assertEquals(11L, ((org.bson.Document) captor.getValue().getQueryObject().get("sequence")).get("$lt"));
    }

    @Test
    void recoveryPageUsesAscendingOrder() {
        when(mongo.exists(any(Query.class), eq(Room.class))).thenReturn(true);
        when(mongo.find(any(Query.class), eq(Message.class))).thenReturn(List.of(message(5), message(7)));
        var page = service.history("room", null, 4L, 50);
        assertEquals(List.of(5L, 7L), page.messages().stream().map(Message::getSequence).toList());
        assertFalse(page.hasMore());
        assertEquals(7L, page.nextCursor());
    }

    private Message message(long sequence) {
        Message message = new Message();
        message.setSequence(sequence);
        return message;
    }
}
