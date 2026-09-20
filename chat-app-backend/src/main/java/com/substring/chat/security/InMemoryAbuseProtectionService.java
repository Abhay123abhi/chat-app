package com.substring.chat.security;

import com.substring.chat.config.ChatProperties;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class InMemoryAbuseProtectionService implements AbuseProtectionService {
    private final ChatProperties properties;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> websocketConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> websocketSessions = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    public InMemoryAbuseProtectionService(ChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean allowGlobalRequest(String clientId) {
        return consume("global:" + clientId, properties.getGlobalRequestRateLimitPerMinute(), Duration.ofMinutes(1));
    }

    @Override
    public boolean allowRoomCreation(String clientId) {
        return consume("room-create:" + clientId, properties.getRoomCreationRateLimitPerHour(), Duration.ofHours(1));
    }

    @Override
    public boolean allowMessage(String clientId) {
        return consume("message:" + clientId, properties.getMessageRateLimitPerMinute(), Duration.ofMinutes(1));
    }

    @Override
    public boolean allowMessageBurst(String clientId) {
        return consume("message-burst:" + clientId, properties.getMessageBurstLimit(),
                Duration.ofSeconds(Math.max(1, properties.getMessageBurstWindowSeconds())));
    }

    @Override
    public synchronized boolean tryOpenWebSocket(String sessionId, String clientId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        if (websocketSessions.containsKey(sessionId)) return true;
        int current = websocketConnections.getOrDefault(clientId, 0);
        if (current >= Math.max(1, properties.getMaxWebsocketConnectionsPerIp())) return false;
        websocketConnections.put(clientId, current + 1);
        websocketSessions.put(sessionId, clientId);
        return true;
    }

    @Override
    public synchronized void closeWebSocket(String sessionId) {
        String clientId = websocketSessions.remove(sessionId);
        if (clientId == null) return;
        websocketConnections.computeIfPresent(clientId, (key, count) -> count <= 1 ? null : count - 1);
    }

    private boolean consume(String key, int configuredLimit, Duration window) {
        int limit = Math.max(1, configuredLimit);
        long now = System.currentTimeMillis();
        long resetAt = now + Math.max(1000L, window.toMillis());
        AtomicBoolean allowed = new AtomicBoolean(false);
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

    private record Window(int count, long resetAt) {}
}
