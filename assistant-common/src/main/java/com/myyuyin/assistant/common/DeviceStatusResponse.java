package com.myyuyin.assistant.common;

public record DeviceStatusResponse(
        String deviceCode,
        String clientName,
        boolean online,
        String state,
        long lastSeenAt,
        AlarmView activeAlarm
) {
}
