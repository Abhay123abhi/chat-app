package com.substring.chat.services;

import com.substring.chat.entities.Message;
import com.substring.chat.entities.Room;
import com.substring.chat.playload.MessageRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageService {
    private final MongoTemplate mongo;
    // Bounded locks order allocation and insertion in one writer process.
    // This is not cross-instance coordination; see docs/architecture.md.
    private final ReentrantLock[] writers = new ReentrantLock[256];

    public MessageService(MongoTemplate mongo) {
        this.mongo = mongo;
        for (int i = 0; i < writers.length; i++) writers[i] = new ReentrantLock();
    }

    public static String validRoom(String roomId) {
        if (roomId == null || !roomId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room ID must contain 1-64 letters, digits, underscores or hyphens");
        }
        return roomId;
    }

    public Message save(String roomId, MessageRequest request) {
        validRoom(roomId);
        if (request == null || request.clientMessageId() == null
                || !request.clientMessageId().matches("[A-Za-z0-9_-]{1,80}")
                || request.sender() == null || request.sender().isBlank() || request.sender().length() > 50
                || request.content() == null || request.content().isBlank() || request.content().length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message: name max 50, text max 4000, request ID required");
        }
        ReentrantLock writer = writers[Math.floorMod(roomId.hashCode(), writers.length)];
        try {
            if (!writer.tryLock(5, TimeUnit.SECONDS)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Room is busy; retry with the same request ID");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Send interrupted; retry with the same request ID");
        }
        try {
            Query identity = Query.query(Criteria.where("roomId").is(roomId)
                    .and("clientMessageId").is(request.clientMessageId()));
            Message existing = mongo.findOne(identity, Message.class);
            if (existing != null) {
                if (!Objects.equals(existing.getSender(), request.sender().trim())
                        || !Objects.equals(existing.getContent(), request.content().trim())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Request ID already used for different content");
                }
                return existing;
            }
            Room room = mongo.findAndModify(Query.query(Criteria.where("roomId").is(roomId)),
                    new Update().inc("nextSequence", 1), FindAndModifyOptions.options().returnNew(true), Room.class);
            if (room == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
            Message message = new Message();
            message.setRoomId(roomId);
            message.setClientMessageId(request.clientMessageId());
            message.setSequence(room.getNextSequence());
            message.setSender(request.sender().trim());
            message.setContent(request.content().trim());
            message.setTimeStamp(Instant.now());
            return mongo.insert(message);
        } finally {
            writer.unlock();
        }
    }

    public MessagePage history(String roomId, Long before, Long after, int limit) {
        validRoom(roomId);
        if (limit < 1 || limit > 100 || (before != null && after != null)
                || (before != null && before < 1) || (after != null && after < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use one valid cursor and a limit from 1 to 100");
        }
        if (!mongo.exists(Query.query(Criteria.where("roomId").is(roomId)), Room.class)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        Criteria criteria = Criteria.where("roomId").is(roomId);
        if (before != null) criteria = criteria.and("sequence").lt(before);
        if (after != null) criteria = criteria.and("sequence").gt(after);
        Query query = Query.query(criteria)
                .with(Sort.by(after == null ? Sort.Direction.DESC : Sort.Direction.ASC, "sequence")).limit(limit + 1);
        List<Message> found = mongo.find(query, Message.class);
        boolean hasMore = found.size() > limit;
        List<Message> page = new ArrayList<>(found.subList(0, Math.min(limit, found.size())));
        if (after == null) Collections.reverse(page);
        Long next = page.isEmpty() ? null
                : (after == null ? page.get(0).getSequence() : page.get(page.size() - 1).getSequence());
        return new MessagePage(page, hasMore, next);
    }

    public record MessagePage(List<Message> messages, boolean hasMore, Long nextCursor) {}
}
