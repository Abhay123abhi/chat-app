package com.substring.chat.security;

import com.substring.chat.config.GuardProperties;
import com.substring.chat.security.DemoGuard.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DemoGuardTest {
    @Test
    void limitsRoomCreationPerClientAndGloballyWithoutCountingRejectedRequests() {
        var p = new GuardProperties();
        p.setEnabled(true);
        p.setRoomCreationsPerIpPerHour(2);
        p.setRoomCreationsGlobalPerHour(3);
        var guard = new DemoGuard(p);
        assertTrue(guard.allowRequest("first", Action.CREATE_ROOM));
        assertTrue(guard.allowRequest("first", Action.CREATE_ROOM));
        assertFalse(guard.allowRequest("first", Action.CREATE_ROOM));
        assertTrue(guard.allowRequest("second", Action.CREATE_ROOM));
        assertFalse(guard.allowRequest("third", Action.CREATE_ROOM));
    }

    @Test
    void burstAndGlobalLimitsStopMessageFloodAcrossManyClients() {
        var p = new GuardProperties();
        p.setEnabled(true);
        p.setMessagesPerIpPerMinute(100);
        p.setMessagesGlobalPerMinute(4);
        p.setMessageBurstPerFiveSeconds(2);
        var guard = new DemoGuard(p);
        assertTrue(guard.allowRequest("first", Action.SEND_MESSAGE));
        assertTrue(guard.allowRequest("first", Action.SEND_MESSAGE));
        assertFalse(guard.allowRequest("first", Action.SEND_MESSAGE));
        assertTrue(guard.allowRequest("second", Action.SEND_MESSAGE));
        assertTrue(guard.allowRequest("third", Action.SEND_MESSAGE));
        assertFalse(guard.allowRequest("fourth", Action.SEND_MESSAGE));
    }

    @Test
    void socketsAreLimitedBeforeConnectAndReleasedOnDisconnect() {
        var p = new GuardProperties();
        p.setEnabled(true);
        p.setMaxConnectionsGlobal(3);
        p.setMaxConnectionsPerIp(2);
        p.setMaxConnectionsPerRoom(1);
        var guard = new DemoGuard(p);
        assertTrue(guard.openSocket("s1", "ip1"));
        assertTrue(guard.joinSocketRoom("s1", "room"));
        assertTrue(guard.openSocket("s2", "ip1"));
        assertFalse(guard.joinSocketRoom("s2", "room"));
        assertFalse(guard.openSocket("s3", "ip1"));
        assertTrue(guard.openSocket("s4", "ip2"));
        assertFalse(guard.openSocket("s5", "ip2"));
        guard.closeSocket("s1");
        assertTrue(guard.joinSocketRoom("s2", "room"));
        assertEquals(2, guard.activeSockets());
    }

    @Test
    void guardIsOptInSoLocalDevelopmentRemainsUnchanged() {
        var guard = new DemoGuard(new GuardProperties());
        for (int i = 0; i < 200; i++) {
            assertTrue(guard.allowRequest("local", Action.CREATE_ROOM));
            assertTrue(guard.openSocket("s" + i, "local"));
        }
    }
}
