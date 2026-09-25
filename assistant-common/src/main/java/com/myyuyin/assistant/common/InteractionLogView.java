package com.myyuyin.assistant.common;

public record InteractionLogView(
        long id,
        String deviceCode,
        String inputText,
        String replyText,
        CommandType commandType,
        boolean success,
        long occurredAt
) {
}
