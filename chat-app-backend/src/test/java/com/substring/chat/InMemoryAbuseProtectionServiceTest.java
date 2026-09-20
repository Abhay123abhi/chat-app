package com.substring.chat;

import com.substring.chat.config.ChatProperties;
import com.substring.chat.security.InMemoryAbuseProtectionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAbuseProtectionServiceTest {
    @Test
    void enforcesMessageAndWebsocketLimits() {
        var properties = new ChatProperties();
        properties.setMessageRateLimitPerMinute(2);
        properties.setGlobalMessageRateLimitPerMinute(10);
        properties.setMaxWebsocketConnectionsPerIp(2);
        properties.setMaxWebsocketConnectionsPerRoom(3);
        properties.setMaxWebsocketConnectionsGlobal(4);

        var protection = new InMemoryAbuseProtectionService(properties);
        assertTrue(protection.allowMessage("client"));
        assertTrue(protection.allowMessage("client"));
        assertFalse(protection.allowMessage("client"));
        assertTrue(protection.tryOpenWebSocket("s1", "client", "room"));
        assertTrue(protection.tryOpenWebSocket("s2", "client", "room"));
        assertFalse(protection.tryOpenWebSocket("s3", "client", "room"));
        protection.closeWebSocket("s1");
        assertTrue(protection.tryOpenWebSocket("s3", "client", "room"));
    }

    @Test
    void globalMessageLimitProtectsAgainstManyClients() {
        var properties = new ChatProperties();
        properties.setMessageRateLimitPerMinute(100);
        properties.setGlobalMessageRateLimitPerMinute(2);

        var protection = new InMemoryAbuseProtectionService(properties);
        assertTrue(protection.allowMessage("client-a"));
        assertTrue(protection.allowMessage("client-b"));
        assertFalse(protection.allowMessage("client-c"));
    }
}
