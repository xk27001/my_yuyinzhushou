package com.myyuyin.assistant.common;

public record WebSocketEvent(
        String type,
        String deviceCode,
        String message,
        String audioWavBase64,
        long occurredAt
) {
}
