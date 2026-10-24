package com.substring.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Optional limits for a small single-instance public demo; disabled for local development. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat.guard")
public class GuardProperties {
    private boolean enabled = false;
    private boolean chatEnabled = true;
    private boolean roomCreationEnabled = true;
    private int requestsPerIpPerMinute = 60;
    private int requestsGlobalPerMinute = 300;
    private int messagesPerIpPerMinute = 20;
    private int messagesGlobalPerMinute = 60;
    private int messageBurstPerFiveSeconds = 4;
    private int roomCreationsPerIpPerHour = 3;
    private int roomCreationsGlobalPerHour = 10;
    private int maxRooms = 25;
    private int maxMessagesPerRoom = 1000;
    private int maxConnectionsPerIp = 5;
    private int maxConnectionsPerRoom = 22;
    private int maxConnectionsGlobal = 50;
    private int maxActiveMembersPerRoom = 20;
    private int maxRememberedMembersPerRoom = 30;
    private int retentionDays = 7;
}
