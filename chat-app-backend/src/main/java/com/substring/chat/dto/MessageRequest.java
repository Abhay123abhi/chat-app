package com.substring.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MessageRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{1,80}")
        String clientMessageId,

        @NotBlank
        @Size(max = 50)
        String sender,

        @NotBlank
        @Size(max = 4000)
        String content) {
}
