package com.myyuyin.assistant.server.repository;

public record AlarmRecord(long id, long deviceId, String deviceCode, String clientName,
                          int durationMinutes, long nextFireAt, String status) {
}
