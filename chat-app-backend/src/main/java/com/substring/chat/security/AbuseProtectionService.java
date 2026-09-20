package com.substring.chat.security;

public interface AbuseProtectionService {
    boolean allowGlobalRequest(String clientId);
    boolean allowRoomCreation(String clientId);
    boolean allowMessage(String clientId);
    boolean allowMessageBurst(String clientId);
    boolean tryOpenWebSocket(String sessionId, String clientId);
    void closeWebSocket(String sessionId);
}
