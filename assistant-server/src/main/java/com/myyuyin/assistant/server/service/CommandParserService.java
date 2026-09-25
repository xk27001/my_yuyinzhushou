package com.myyuyin.assistant.server.service;

import com.myyuyin.assistant.common.CommandType;
import com.myyuyin.assistant.server.repository.CommandRuleRecord;
import com.myyuyin.assistant.server.repository.CommandRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class CommandParserService {
    private final CommandRuleRepository ruleRepository;

    public CommandParserService(CommandRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public ParsedCommand parse(String normalizedText) {
        for (CommandRuleRecord rule : ruleRepository.listEnabled()) {
            if ("WAKE".equals(rule.commandType())) {
                continue;
            }
            if ("ALIAS".equalsIgnoreCase(rule.matchType())) {
                String alias = TextNormalizer.normalize(rule.patternText());
                if (!alias.isBlank() && normalizedText.contains(alias)) {
                    return new ParsedCommand(type(rule), rule, null);
                }
                continue;
            }
            if ("REGEX".equalsIgnoreCase(rule.matchType())) {
                try {
                    Matcher matcher = Pattern.compile(rule.patternText(), Pattern.DOTALL)
                            .matcher(normalizedText);
                    if (matcher.matches()) {
                        return new ParsedCommand(type(rule), rule, matcher);
                    }
                } catch (PatternSyntaxException ignored) {
                    // Invalid database rules are skipped so one bad row cannot stop the assistant.
                }
            }
        }
        return null;
    }

    public int extractDurationMinutes(ParsedCommand command) {
        if (command == null || command.type() != CommandType.ALARM_CREATE || command.matcher() == null) {
            return 0;
        }
        String numberText = command.matcher().group(1);
        String unit = command.matcher().group(2);
        int number = ChineseNumberParser.parse(numberText);
        return "小时".equals(unit) ? number * 60 : number;
    }

    public String render(String template, Map<String, Object> values) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    public List<CommandRuleRecord> listRules() {
        return ruleRepository.listEnabled();
    }

    private CommandType type(CommandRuleRecord rule) {
        try {
            return CommandType.valueOf(rule.commandType());
        } catch (IllegalArgumentException ex) {
            return CommandType.UNKNOWN;
        }
    }
}
