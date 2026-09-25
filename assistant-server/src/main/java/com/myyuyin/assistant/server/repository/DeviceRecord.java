package com.myyuyin.assistant.server.repository;

public record DeviceRecord(long id, String deviceCode, String clientName, String status,
                           String state, Long lastSeenAt) {
}
