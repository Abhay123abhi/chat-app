package com.substring.chat.validation;

import com.substring.chat.dto.MessageRequest;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ChatValidation {
    private static final Pattern ROOM_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,80}");

    private ChatValidation() {
    }

    public static String validRoomId(String roomId) {
        if (roomId == null || !ROOM_ID.matcher(roomId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Room ID must contain 1-64 letters, digits, underscores or hyphens");
        }
        return roomId;
    }

    public static void validMessage(MessageRequest request) {
        if (request == null
                || request.clientMessageId() == null
                || !REQUEST_ID.matcher(request.clientMessageId()).matches()
                || request.sender() == null
                || request.sender().isBlank()
                || request.sender().trim().length() > 50
                || request.content() == null
                || request.content().isBlank()
                || request.content().trim().length() > 4000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid message: name max 50, text max 4000, request ID required");
        }
    }
}
