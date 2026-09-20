package com.substring.chat.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    private boolean enabled = true;
    private boolean roomCreationEnabled = true;
    private int requestRateLimitPerMinute = 120;
    private int globalRequestRateLimitPerMinute = 600;
    private int messageRateLimitPerMinute = 30;
    private int globalMessageRateLimitPerMinute = 300;
    private int messageBurstLimit = 5;
    private int messageBurstWindowSeconds = 5;
    private int roomCreationRateLimitPerHour = 5;
    private int globalRoomCreationRateLimitPerHour = 30;
    private int maxMessageLength = 4000;
    private int maxMessagesPerRoom = 2000;
    private int maxActiveRooms = 100;
    private int maxActiveUsersPerRoom = 20;
    private int maxPresenceMembersPerRoom = 50;
    private int maxWebsocketConnectionsPerIp = 5;
    private int maxWebsocketConnectionsPerRoom = 25;
    private int maxWebsocketConnectionsGlobal = 80;
    private int messageRetentionDays = 7;
    private int roomInactivityDays = 7;
    private int defaultHistoryPageSize = 50;
    private int maxHistoryPageSize = 100;
    private String trustedClientIpHeader = "CF-Connecting-IP";
}
