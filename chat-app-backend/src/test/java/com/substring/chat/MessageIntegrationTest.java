package com.substring.chat;

import com.mongodb.client.MongoClients;
import com.substring.chat.dto.MessageRequest;
import com.substring.chat.entities.Message;
import com.substring.chat.entities.Room;
import com.substring.chat.services.MessageService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MessageIntegrationTest {
    @Container
    static MongoDBContainer database = new MongoDBContainer("mongo:8.0");

    @Test
    void concurrentMessagesAndRetriesSurviveWithoutDuplicates() throws Exception {
        try (var client = MongoClients.create(database.getReplicaSetUrl())) {
            var mongo = new MongoTemplate(client, "chat_test");
            mongo.dropCollection(Message.class);
            mongo.dropCollection(Room.class);
            mongo.indexOps(Message.class).ensureIndex(
                    new Index().on("roomId", Direction.ASC).on("sequence", Direction.ASC).unique());
            mongo.indexOps(Message.class).ensureIndex(
                    new Index().on("roomId", Direction.ASC).on("clientMessageId", Direction.ASC).unique());

            var room = new Room();
            room.setRoomId("concurrent");
            mongo.insert(room);

            var service = new MessageService(mongo);
            ExecutorService executor = Executors.newFixedThreadPool(12);

            try {
                List<Callable<Message>> work = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    int id = i;
                    work.add(() -> service.save(
                            "concurrent",
                            new MessageRequest("key-" + id, "Guest", "message " + id)));
                }

                for (Future<Message> future : executor.invokeAll(work)) {
                    future.get(20, TimeUnit.SECONDS);
                }

                List<Callable<Message>> retries = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    retries.add(() -> service.save(
                            "concurrent",
                            new MessageRequest("key-0", "Guest", "message 0")));
                }

                var results = new HashSet<String>();
                for (Future<Message> future : executor.invokeAll(retries)) {
                    results.add(future.get().getId());
                }

                assertEquals(1, results.size());
                assertEquals(100, mongo.getCollection("messages").countDocuments());

                var recovered = service.history("concurrent", null, 0L, 60);
                assertEquals(60, recovered.messages().size());
                assertTrue(recovered.hasMore());

                var rest = service.history("concurrent", null, recovered.nextCursor(), 60);
                assertEquals(40, rest.messages().size());
                assertFalse(rest.hasMore());
                assertTrue(rest.messages().getFirst().getSequence() > recovered.nextCursor());
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
