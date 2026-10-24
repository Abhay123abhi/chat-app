package com.substring.chat.services;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {
    @Test
    void inactiveMembersDisappearButOnlineMembersRemain() {
        var service = new PresenceService();
        service.connect("a", "room", "Alice");
        service.disconnect("a");
        service.connect("b", "room", "Bob");
        service.evictInactiveBefore(Instant.now().plusSeconds(5));
        var members = service.snapshot("room").members();
        assertEquals(1, members.size());
        assertEquals("Bob", members.getFirst().name());
        assertTrue(members.getFirst().online());
    }

    @Test
    void multipleSessionsForSameMemberRemainOnlineUntilLastDisconnect() {
        var service = new PresenceService();
        service.connect("a", "room", "Alice");
        service.connect("b", "room", "ALICE");
        service.disconnect("a");
        assertTrue(service.snapshot("room").members().getFirst().online());
        service.disconnect("b");
        assertFalse(service.snapshot("room").members().getFirst().online());
        service.evictInactiveBefore(Instant.now().plusSeconds(5));
        assertTrue(service.snapshot("room").members().isEmpty());
    }
}
