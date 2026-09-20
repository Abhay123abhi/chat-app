package com.substring.chat.dto;

import com.substring.chat.entities.Message;
import java.util.List;

public record MessagePageResponse(
        List<Message> messages,
        boolean hasMore,
        Long nextCursor) {
}
