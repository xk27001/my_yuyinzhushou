package com.myyuyin.assistant.common;

public record ConfigItem(
        String key,
        String value,
        String valueType,
        String description,
        boolean editable
) {
}
