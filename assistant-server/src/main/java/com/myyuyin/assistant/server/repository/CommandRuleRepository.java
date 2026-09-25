package com.myyuyin.assistant.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CommandRuleRepository {
    private final JdbcTemplate jdbc;

    public CommandRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CommandRuleRecord> listEnabled() {
        return jdbc.query("""
                SELECT command_type, match_type, pattern_text, response_template, priority
                FROM va_command_rule WHERE sfyx = 1 AND enabled = 1
                ORDER BY priority DESC, id ASC
                """, (rs, rowNum) -> new CommandRuleRecord(
                rs.getString("command_type"), rs.getString("match_type"), rs.getString("pattern_text"),
                rs.getString("response_template"), rs.getInt("priority")));
    }

    public CommandRuleRecord findEnabledByType(String commandType) {
        List<CommandRuleRecord> rules = jdbc.query("""
                SELECT command_type, match_type, pattern_text, response_template, priority
                FROM va_command_rule WHERE command_type = ? AND sfyx = 1 AND enabled = 1
                ORDER BY priority DESC, id ASC LIMIT 1
                """, (rs, rowNum) -> new CommandRuleRecord(
                rs.getString("command_type"), rs.getString("match_type"), rs.getString("pattern_text"),
                rs.getString("response_template"), rs.getInt("priority")), commandType);
        return rules.isEmpty() ? null : rules.getFirst();
    }

    public List<CommandRuleRecord> listAllActive() {
        return jdbc.query("""
                SELECT command_type, match_type, pattern_text, response_template, priority
                FROM va_command_rule WHERE sfyx = 1 ORDER BY priority DESC, id ASC
                """, (rs, rowNum) -> new CommandRuleRecord(
                rs.getString("command_type"), rs.getString("match_type"), rs.getString("pattern_text"),
                rs.getString("response_template"), rs.getInt("priority")));
    }

    public boolean softDelete(long id) {
        return jdbc.update("UPDATE va_command_rule SET sfyx = 0 WHERE id = ? AND sfyx = 1", id) > 0;
    }
}
