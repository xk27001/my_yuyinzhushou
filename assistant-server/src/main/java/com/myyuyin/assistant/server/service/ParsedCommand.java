package com.myyuyin.assistant.server.service;

import com.myyuyin.assistant.common.CommandType;
import com.myyuyin.assistant.server.repository.CommandRuleRecord;

import java.util.regex.Matcher;

public record ParsedCommand(CommandType type, CommandRuleRecord rule, Matcher matcher) {
}
