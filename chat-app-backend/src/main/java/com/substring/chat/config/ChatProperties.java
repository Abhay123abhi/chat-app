package com.substring.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    private boolean enabled = true;
    private boolean roomCreationEnabled = true;
    private int messageRateLimitPerMinute = 30;
    private int messageBurstLimit = 5;
    private int messageBurstWindowSeconds = 5;
    private int roomCreationRateLimitPerHour = 5;
    private int maxMessageLength = 4000;
    private int maxMessagesPerRoom = 2000;
    private int maxActiveUsersPerRoom = 20;
    private int maxWebsocketConnectionsPerIp = 5;
    private int messageRetentionDays = 7;
    private int roomInactivityDays = 7;
    private int globalRequestRateLimitPerMinute = 120;
    private int defaultHistoryPageSize = 50;
    private int maxHistoryPageSize = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRoomCreationEnabled() { return roomCreationEnabled; }
    public void setRoomCreationEnabled(boolean roomCreationEnabled) { this.roomCreationEnabled = roomCreationEnabled; }
    public int getMessageRateLimitPerMinute() { return messageRateLimitPerMinute; }
    public void setMessageRateLimitPerMinute(int value) { this.messageRateLimitPerMinute = value; }
    public int getMessageBurstLimit() { return messageBurstLimit; }
    public void setMessageBurstLimit(int value) { this.messageBurstLimit = value; }
    public int getMessageBurstWindowSeconds() { return messageBurstWindowSeconds; }
    public void setMessageBurstWindowSeconds(int value) { this.messageBurstWindowSeconds = value; }
    public int getRoomCreationRateLimitPerHour() { return roomCreationRateLimitPerHour; }
    public void setRoomCreationRateLimitPerHour(int value) { this.roomCreationRateLimitPerHour = value; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public void setMaxMessageLength(int value) { this.maxMessageLength = value; }
    public int getMaxMessagesPerRoom() { return maxMessagesPerRoom; }
    public void setMaxMessagesPerRoom(int value) { this.maxMessagesPerRoom = value; }
    public int getMaxActiveUsersPerRoom() { return maxActiveUsersPerRoom; }
    public void setMaxActiveUsersPerRoom(int value) { this.maxActiveUsersPerRoom = value; }
    public int getMaxWebsocketConnectionsPerIp() { return maxWebsocketConnectionsPerIp; }
    public void setMaxWebsocketConnectionsPerIp(int value) { this.maxWebsocketConnectionsPerIp = value; }
    public int getMessageRetentionDays() { return messageRetentionDays; }
    public void setMessageRetentionDays(int value) { this.messageRetentionDays = value; }
    public int getRoomInactivityDays() { return roomInactivityDays; }
    public void setRoomInactivityDays(int value) { this.roomInactivityDays = value; }
    public int getGlobalRequestRateLimitPerMinute() { return globalRequestRateLimitPerMinute; }
    public void setGlobalRequestRateLimitPerMinute(int value) { this.globalRequestRateLimitPerMinute = value; }
    public int getDefaultHistoryPageSize() { return defaultHistoryPageSize; }
    public void setDefaultHistoryPageSize(int value) { this.defaultHistoryPageSize = value; }
    public int getMaxHistoryPageSize() { return maxHistoryPageSize; }
    public void setMaxHistoryPageSize(int value) { this.maxHistoryPageSize = value; }
}
