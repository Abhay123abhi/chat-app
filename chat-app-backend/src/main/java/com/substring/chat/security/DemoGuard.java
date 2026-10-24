package com.substring.chat.security;

import com.substring.chat.config.GuardProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Bounded, in-memory limits for ONE public-demo backend instance.
 * Enforces combined limits atomically; an attacker cannot grow the counter map indefinitely.
 * For horizontal scaling, enforce distributed limits at the gateway or a shared store.
 */
@Service
public class DemoGuard {
    public enum Action { READ, CREATE_ROOM, SEND_MESSAGE }

    private static final int MAX_COUNTER_KEYS = 4096;
    private final GuardProperties limits;
    private final Map<String, Window> counters = new HashMap<>();
    private final Map<String, Socket> sockets = new HashMap<>();
    private long operations;

    public DemoGuard(GuardProperties limits) {
        this.limits = limits;
    }

    public synchronized boolean allowRequest(String address, Action action) {
        if (!limits.isEnabled()) return true;
        long now = System.currentTimeMillis();
        if (++operations % 128 == 0 || counters.size() >= MAX_COUNTER_KEYS) {
            counters.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }

        var rules = new ArrayList<Rule>();
        rules.add(new Rule("api:all", positive(limits.getRequestsGlobalPerMinute()), 60_000));
        rules.add(new Rule("api:ip:" + address, positive(limits.getRequestsPerIpPerMinute()), 60_000));
        if (action == Action.CREATE_ROOM) {
            rules.add(new Rule("rooms:all", positive(limits.getRoomCreationsGlobalPerHour()), 3_600_000));
            rules.add(new Rule("rooms:ip:" + address, positive(limits.getRoomCreationsPerIpPerHour()), 3_600_000));
        }
        if (action == Action.SEND_MESSAGE) {
            rules.add(new Rule("messages:all", positive(limits.getMessagesGlobalPerMinute()), 60_000));
            rules.add(new Rule("messages:ip:" + address, positive(limits.getMessagesPerIpPerMinute()), 60_000));
            rules.add(new Rule("messages:burst:" + address, positive(limits.getMessageBurstPerFiveSeconds()), 5_000));
        }

        int newKeys = 0;
        for (var rule : rules) {
            var existing = counters.get(rule.key());
            if (existing == null || existing.expiresAt() <= now) {
                if (existing == null) newKeys++;
            } else if (existing.count() >= rule.limit()) {
                return false;
            }
        }
        // Deny new identities when full, rather than holding arbitrarily many attacker-controlled keys.
        if (counters.size() + newKeys > MAX_COUNTER_KEYS) return false;
        for (var rule : rules) {
            var existing = counters.get(rule.key());
            if (existing == null || existing.expiresAt() <= now) {
                counters.put(rule.key(), new Window(1, now + rule.periodMs()));
            } else {
                counters.put(rule.key(), new Window(existing.count() + 1, existing.expiresAt()));
            }
        }
        return true;
    }

    /** Reserve at WebSocket establishment, BEFORE any STOMP CONNECT frame arrives. */
    public synchronized boolean openSocket(String sessionId, String address) {
        if (!limits.isEnabled()) return true;
        if (sockets.containsKey(sessionId)) return true;
        if (sockets.size() >= positive(limits.getMaxConnectionsGlobal())
                || sockets.values().stream().filter(s -> s.address().equals(address)).count()
                >= positive(limits.getMaxConnectionsPerIp())) return false;
        sockets.put(sessionId, new Socket(address, null));
        return true;
    }

    public synchronized boolean joinSocketRoom(String sessionId, String roomId) {
        if (!limits.isEnabled()) return true;
        var socket = sockets.get(sessionId);
        if (socket == null) return false;
        if (roomId.equals(socket.roomId())) return true;
        if (socket.roomId() != null) return false;
        if (sockets.values().stream().filter(s -> roomId.equals(s.roomId())).count()
                >= positive(limits.getMaxConnectionsPerRoom())) return false;
        sockets.put(sessionId, new Socket(socket.address(), roomId));
        return true;
    }

    public synchronized void closeSocket(String sessionId) {
        sockets.remove(sessionId);
    }

    public synchronized int activeSockets() {
        return sockets.size();
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }

    private record Window(int count, long expiresAt) {}
    private record Rule(String key, int limit, long periodMs) {}
    private record Socket(String address, String roomId) {}
}
