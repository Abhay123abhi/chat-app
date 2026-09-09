package com.substring.chat.playload;

public record MessageRequest(String clientMessageId, String sender, String content) {}
