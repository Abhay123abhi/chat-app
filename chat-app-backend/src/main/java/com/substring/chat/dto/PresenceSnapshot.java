package com.substring.chat.dto;

import java.util.List;

public record PresenceSnapshot(
        String roomId,
        List<MemberPresence> members) {
}
