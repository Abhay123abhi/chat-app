package com.substring.chat.services;

import com.substring.chat.config.ChatProperties;
import com.substring.chat.dto.MessagePageResponse;
import com.substring.chat.dto.MessageRequest;
import com.substring.chat.entities.Message;
import com.substring.chat.entities.Room;
import com.substring.chat.validation.ChatValidation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final int ABSOLUTE_MAX_MESSAGE_LENGTH = 4000;
    private static final int ABSOLUTE_MAX_HISTORY_PAGE_SIZE = 100;
    private static final int WRITER_STRIPES = 256;
    private static final long WRITER_LOCK_TIMEOUT_SECONDS = 5L;

    private final MongoTemplate mongo;
    private final ChatProperties properties;
    private final ReentrantLock[] writers = IntStream.range(0, WRITER_STRIPES)
            .mapToObj(ignored -> new ReentrantLock())
            .toArray(ReentrantLock[]::new);

    public Message save(String roomId, MessageRequest request) {
        ChatValidation.validRoomId(roomId);
        ChatValidation.validMessage(request);

        var maxMessageLength = Math.min(ABSOLUTE_MAX_MESSAGE_LENGTH, Math.max(1, properties.getMaxMessageLength()));
        if (request.content().trim().length() > maxMessageLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message exceeds the configured length limit");
        }

        var writer = writers[Math.floorMod(roomId.hashCode(), writers.length)];
        acquireWriter(writer);

        try {
            var identity = Query.query(Criteria.where("roomId").is(roomId)
                    .and("clientMessageId").is(request.clientMessageId()));
            var existing = mongo.findOne(identity, Message.class);
            if (existing != null) {
                ensureSamePayload(existing, request);
                return existing;
            }

            var maxMessages = Math.max(1, properties.getMaxMessagesPerRoom());
            var roomAndQuota = new Criteria().andOperator(
                    Criteria.where("roomId").is(roomId),
                    new Criteria().orOperator(
                            Criteria.where("messageCount").lt(maxMessages),
                            Criteria.where("messageCount").exists(false)));

            var now = Instant.now();
            var room = mongo.findAndModify(
                    Query.query(roomAndQuota),
                    new Update()
                            .inc("nextSequence", 1)
                            .inc("messageCount", 1)
                            .set("lastActivityAt", now)
                            .set("expiresAt", now.plus(Math.max(1, properties.getRoomInactivityDays()), ChronoUnit.DAYS)),
                    FindAndModifyOptions.options().returnNew(true),
                    Room.class);

            if (room == null) {
                var exists = mongo.exists(Query.query(Criteria.where("roomId").is(roomId)), Room.class);
                if (!exists) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Room has reached its message limit");
            }

            var message = new Message();
            message.setRoomId(roomId);
            message.setClientMessageId(request.clientMessageId());
            message.setSequence(room.getNextSequence());
            message.setSender(request.sender().trim());
            message.setContent(request.content().trim());
            message.setTimeStamp(now);
            message.setExpiresAt(now.plus(Math.max(1, properties.getMessageRetentionDays()), ChronoUnit.DAYS));
            return mongo.insert(message);
        } finally {
            writer.unlock();
        }
    }

    public MessagePageResponse history(String roomId, Long before, Long after, int limit) {
        ChatValidation.validRoomId(roomId);
        var maxPageSize = Math.min(ABSOLUTE_MAX_HISTORY_PAGE_SIZE,
                Math.max(1, properties.getMaxHistoryPageSize()));
        validateHistoryCursor(before, after, limit, maxPageSize);

        if (!mongo.exists(Query.query(Criteria.where("roomId").is(roomId)), Room.class)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }

        var criteria = Criteria.where("roomId").is(roomId);
        if (before != null) criteria = criteria.and("sequence").lt(before);
        if (after != null) criteria = criteria.and("sequence").gt(after);

        var direction = after == null ? Sort.Direction.DESC : Sort.Direction.ASC;
        var query = Query.query(criteria).with(Sort.by(direction, "sequence")).limit(limit + 1);

        var found = mongo.find(query, Message.class);
        var hasMore = found.size() > limit;
        var page = new ArrayList<>(found.subList(0, Math.min(limit, found.size())));
        if (after == null) Collections.reverse(page);

        var nextCursor = page.isEmpty() ? null
                : after == null ? page.getFirst().getSequence() : page.getLast().getSequence();
        return new MessagePageResponse(List.copyOf(page), hasMore, nextCursor);
    }

    private static void acquireWriter(ReentrantLock writer) {
        try {
            if (!writer.tryLock(WRITER_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Room is busy; retry with the same request ID");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Send interrupted; retry with the same request ID");
        }
    }

    private static void ensureSamePayload(Message existing, MessageRequest request) {
        if (!Objects.equals(existing.getSender(), request.sender().trim())
                || !Objects.equals(existing.getContent(), request.content().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request ID already used for different content");
        }
    }

    private static void validateHistoryCursor(Long before, Long after, int limit, int maxPageSize) {
        if (limit < 1 || limit > maxPageSize || (before != null && after != null)
                || (before != null && before < 1) || (after != null && after < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use one valid cursor and a limit from 1 to " + maxPageSize);
        }
    }
}
