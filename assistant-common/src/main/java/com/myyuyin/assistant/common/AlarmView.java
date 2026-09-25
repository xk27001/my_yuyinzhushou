package com.myyuyin.assistant.common;

public record AlarmView(
        long id,
        String deviceCode,
        int durationMinutes,
        long nextFireAt,
        String status
) {
}
