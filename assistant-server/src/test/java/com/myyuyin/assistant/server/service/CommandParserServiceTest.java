package com.myyuyin.assistant.server.service;

import com.myyuyin.assistant.common.CommandType;
import com.myyuyin.assistant.server.repository.CommandRuleRecord;
import com.myyuyin.assistant.server.repository.CommandRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserServiceTest {
    @Test
    void extractsAlarmDurationAndRendersReply() {
        CommandRuleRepository repository = new CommandRuleRepository(null) {
            @Override
            public List<CommandRuleRecord> listEnabled() {
                return List.of(new CommandRuleRecord(
                        "ALARM_CREATE", "REGEX",
                        ".*?([0-9一二三四五六七八九十百零两]+)(分钟|小时).*闹钟.*",
                        "{duration}分钟闹钟已设置。", 100));
            }
        };
        CommandParserService service = new CommandParserService(repository);

        ParsedCommand parsed = service.parse(TextNormalizer.normalize("定一个30分钟的闹钟"));

        assertNotNull(parsed);
        assertEquals(CommandType.ALARM_CREATE, parsed.type());
        assertEquals(30, service.extractDurationMinutes(parsed));
        assertEquals("30分钟闹钟已设置。", service.render(parsed.rule().responseTemplate(), Map.of("duration", 30)));
    }

    @Test
    void normalizesSpokenAndPunctuationText() {
        assertEquals("三角洲几点了", TextNormalizer.normalize("三角洲， 几点了？"));
    }
}
