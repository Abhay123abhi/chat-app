package com.substring.chat.dto;

import java.time.Instant;

public record MemberPresence(
        String name,
        boolean online,
        Instant lastSeen) {
}
