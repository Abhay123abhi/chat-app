package com.substring.chat;

import com.substring.chat.config.ChatProperties;
import com.substring.chat.security.InMemoryAbuseProtectionService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryAbuseProtectionServiceTest {
    @Test
    void messageAndWebsocketLimitsAreEnforced() {
        ChatProperties properties = new ChatProperties();
        properties.setMessageRateLimitPerMinute(2);
        properties.setMaxWebsocketConnectionsPerIp(2);
        InMemoryAbuseProtectionService protection = new InMemoryAbuseProtectionService(properties);

        assertTrue(protection.allowMessage("client"));
        assertTrue(protection.allowMessage("client"));
        assertFalse(protection.allowMessage("client"));

        assertTrue(protection.tryOpenWebSocket("s1", "client"));
        assertTrue(protection.tryOpenWebSocket("s2", "client"));
        assertFalse(protection.tryOpenWebSocket("s3", "client"));
        protection.closeWebSocket("s1");
        assertTrue(protection.tryOpenWebSocket("s3", "client"));
    }
}
