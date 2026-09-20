package com.substring.chat.services;

import com.substring.chat.config.ChatProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {
    private final ChatProperties properties;
    private final ConcurrentHashMap<String, SessionPresence> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, MemberState>> rooms = new ConcurrentHashMap<>();

    public PresenceService(ChatProperties properties) {
        this.properties = properties;
    }

    public void connect(String sessionId, String roomId, String displayName) {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("Session is required");
        String name = validDisplayName(displayName);

        disconnect(sessionId);
        ConcurrentHashMap<String, MemberState> members =
                rooms.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>());
        String memberKey = name.toLowerCase(Locale.ROOT);

        synchronized (members) {
            MemberState member = members.get(memberKey);
            boolean becomingActive = member == null || member.sessions.isEmpty();
            if (becomingActive && activeCount(members) >= Math.max(1, properties.getMaxActiveUsersPerRoom())) {
                throw new IllegalStateException("Room active-user limit reached");
            }
            if (member == null) {
                member = new MemberState(name);
                members.put(memberKey, member);
            }
            member.displayName = name;
            member.sessions.add(sessionId);
            member.lastSeen = Instant.now();
            sessions.put(sessionId, new SessionPresence(roomId, name));
        }
    }

    public String disconnect(String sessionId) {
        if (sessionId == null) return null;
        SessionPresence session = sessions.remove(sessionId);
        if (session == null) return null;

        ConcurrentHashMap<String, MemberState> members = rooms.get(session.roomId());
        if (members == null) return session.roomId();

        synchronized (members) {
            MemberState member = members.get(session.displayName().toLowerCase(Locale.ROOT));
            if (member != null) {
                member.sessions.remove(sessionId);
                member.lastSeen = Instant.now();
            }
        }
        return session.roomId();
    }

    public String roomForSession(String sessionId) {
        SessionPresence session = sessionId == null ? null : sessions.get(sessionId);
        return session == null ? null : session.roomId();
    }

    public PresenceSnapshot snapshot(String roomId) {
        ConcurrentHashMap<String, MemberState> members = rooms.get(roomId);
        if (members == null) return new PresenceSnapshot(roomId, List.of());

        List<MemberPresence> result = new ArrayList<>();
        members.values().forEach(member -> result.add(new MemberPresence(
                member.displayName,
                !member.sessions.isEmpty(),
                member.lastSeen)));
        result.sort(Comparator.comparing(MemberPresence::online).reversed()
                .thenComparing(MemberPresence::name, String.CASE_INSENSITIVE_ORDER));
        return new PresenceSnapshot(roomId, result);
    }

    private static long activeCount(ConcurrentHashMap<String, MemberState> members) {
        return members.values().stream().filter(member -> !member.sessions.isEmpty()).count();
    }

    private static String validDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 50) {
            throw new IllegalArgumentException("Display name must contain 1-50 characters");
        }
        return displayName.trim();
    }

    private record SessionPresence(String roomId, String displayName) {}

    private static final class MemberState {
        private volatile String displayName;
        private final Set<String> sessions = ConcurrentHashMap.newKeySet();
        private volatile Instant lastSeen = Instant.now();

        private MemberState(String displayName) {
            this.displayName = displayName;
        }
    }

    public record MemberPresence(String name, boolean online, Instant lastSeen) {}
    public record PresenceSnapshot(String roomId, List<MemberPresence> members) {}
}
