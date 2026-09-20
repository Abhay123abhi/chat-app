package com.substring.chat.security;

public interface AbuseProtectionService {
    boolean allowRequest(String clientId);
    boolean allowRoomCreation(String clientId);
    boolean allowMessage(String clientId);
    boolean allowMessageBurst(String clientId);
    boolean tryOpenWebSocket(String sessionId, String clientId, String roomId);
    void closeWebSocket(String sessionId);
}
