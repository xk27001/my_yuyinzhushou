package com.myyuyin.assistant.common;

public record VoiceProcessResponse(
        String deviceCode,
        String recognizedText,
        String replyText,
        CommandType commandType,
        boolean ignored,
        String audioWavBase64,
        String audioMimeType,
        long occurredAt
) {
}
