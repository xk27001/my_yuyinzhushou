package com.myyuyin.assistant.server.repository;

public record CommandRuleRecord(String commandType, String matchType, String patternText,
                                String responseTemplate, int priority) {
}
