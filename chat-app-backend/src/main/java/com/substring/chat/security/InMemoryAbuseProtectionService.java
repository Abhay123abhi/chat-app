package com.substring.chat.security;

import com.substring.chat.config.ChatProperties;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InMemoryAbuseProtectionService implements AbuseProtectionService {
    private final ChatProperties properties;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SocketIdentity> websocketSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> websocketConnectionsByIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> websocketConnectionsByRoom = new ConcurrentHashMap<>();
    private final AtomicLong websocketConnectionsGlobal = new AtomicLong();
    private final AtomicLong operations = new AtomicLong();

    @Override public boolean allowRequest(String clientId) {
        return consume("request:global", properties.getGlobalRequestRateLimitPerMinute(), Duration.ofMinutes(1))
                && consume("request:client:" + clientId, properties.getRequestRateLimitPerMinute(), Duration.ofMinutes(1));
    }

    @Override public boolean allowRoomCreation(String clientId) {
        return consume("room-create:global", properties.getGlobalRoomCreationRateLimitPerHour(), Duration.ofHours(1))
                && consume("room-create:client:" + clientId, properties.getRoomCreationRateLimitPerHour(), Duration.ofHours(1));
    }

    @Override public boolean allowMessage(String clientId) {
        return consume("message:global", properties.getGlobalMessageRateLimitPerMinute(), Duration.ofMinutes(1))
                && consume("message:client:" + clientId, properties.getMessageRateLimitPerMinute(), Duration.ofMinutes(1));
    }

    @Override public boolean allowMessageBurst(String clientId) {
        return consume("message-burst:" + clientId, properties.getMessageBurstLimit(),
                Duration.ofSeconds(Math.max(1, properties.getMessageBurstWindowSeconds())));
    }

    @Override public synchronized boolean tryOpenWebSocket(String sessionId, String clientId, String roomId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        if (websocketSessions.containsKey(sessionId)) return true;

        int globalLimit = Math.max(1, properties.getMaxWebsocketConnectionsGlobal());
        int ipLimit = Math.max(1, properties.getMaxWebsocketConnectionsPerIp());
        int roomLimit = Math.max(1, properties.getMaxWebsocketConnectionsPerRoom());
        if (websocketConnectionsGlobal.get() >= globalLimit
                || websocketConnectionsByIp.getOrDefault(clientId, 0) >= ipLimit
                || websocketConnectionsByRoom.getOrDefault(roomId, 0) >= roomLimit) {
            return false;
        }

        websocketSessions.put(sessionId, new SocketIdentity(clientId, roomId));
        websocketConnectionsByIp.merge(clientId, 1, Integer::sum);
        websocketConnectionsByRoom.merge(roomId, 1, Integer::sum);
        websocketConnectionsGlobal.incrementAndGet();
        return true;
    }

    @Override public synchronized void closeWebSocket(String sessionId) {
        var identity = websocketSessions.remove(sessionId);
        if (identity == null) return;
        decrement(websocketConnectionsByIp, identity.clientId());
        decrement(websocketConnectionsByRoom, identity.roomId());
        websocketConnectionsGlobal.updateAndGet(current -> Math.max(0, current - 1));
    }

    private boolean consume(String key, int configuredLimit, Duration window) {
        int limit = Math.max(1, configuredLimit);
        long now = System.currentTimeMillis();
        long resetAt = now + Math.max(1000L, window.toMillis());
        var allowed = new AtomicBoolean(false);
        counters.compute(key, (ignored, current) -> {
            if (current == null || current.resetAt() <= now) {
                allowed.set(true);
                return new Window(1, resetAt);
            }
            if (current.count() >= limit) return current;
            allowed.set(true);
            return new Window(current.count() + 1, current.resetAt());
        });
        if ((operations.incrementAndGet() & 1023) == 0) {
            counters.entrySet().removeIf(entry -> entry.getValue().resetAt() <= now);
        }
        return allowed.get();
    }

    private static void decrement(ConcurrentHashMap<String, Integer> counters, String key) {
        counters.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
    }

    private record Window(int count, long resetAt) {}
    private record SocketIdentity(String clientId, String roomId) {}
}
