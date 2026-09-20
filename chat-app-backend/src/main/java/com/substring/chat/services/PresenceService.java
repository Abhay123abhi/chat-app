package com.substring.chat.services;

import com.substring.chat.dto.MemberPresence;
import com.substring.chat.dto.PresenceSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {
    private final ConcurrentHashMap<String, SessionPresence> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, MemberState>> rooms = new ConcurrentHashMap<>();

    public void connect(String sessionId, String roomId, String displayName) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        var name = validDisplayName(displayName);
        disconnect(sessionId);
        sessions.put(sessionId, new SessionPresence(roomId, name));

        var members = rooms.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>());
        var memberKey = name.toLowerCase(Locale.ROOT);
        var member = members.computeIfAbsent(memberKey, ignored -> new MemberState(name));

        member.displayName = name;
        member.sessions.add(sessionId);
        member.lastSeen = Instant.now();
    }

    public String disconnect(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        var session = sessions.remove(sessionId);
        if (session == null) {
            return null;
        }

        var members = rooms.get(session.roomId());
        if (members == null) {
            return session.roomId();
        }

        var member = members.get(session.displayName().toLowerCase(Locale.ROOT));
        if (member != null) {
            member.sessions.remove(sessionId);
            member.lastSeen = Instant.now();
        }

        return session.roomId();
    }

    public PresenceSnapshot snapshot(String roomId) {
        var members = rooms.get(roomId);
        if (members == null) {
            return new PresenceSnapshot(roomId, List.of());
        }

        var result = members.values().stream()
                .map(member -> new MemberPresence(
                        member.displayName,
                        !member.sessions.isEmpty(),
                        member.lastSeen))
                .sorted(Comparator.comparing(MemberPresence::online).reversed()
                        .thenComparing(MemberPresence::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new PresenceSnapshot(roomId, result);
    }

    private static String validDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 50) {
            throw new IllegalArgumentException("Display name must contain 1-50 characters");
        }
        return displayName.trim();
    }

    private record SessionPresence(String roomId, String displayName) {
    }

    private static final class MemberState {
        private volatile String displayName;
        private final Set<String> sessions = ConcurrentHashMap.newKeySet();
        private volatile Instant lastSeen = Instant.now();

        private MemberState(String displayName) {
            this.displayName = displayName;
        }
    }
}
