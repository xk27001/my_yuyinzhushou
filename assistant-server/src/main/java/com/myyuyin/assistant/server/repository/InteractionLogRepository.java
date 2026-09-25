package com.myyuyin.assistant.server.repository;

import com.myyuyin.assistant.common.CommandType;
import com.myyuyin.assistant.common.InteractionLogView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class InteractionLogRepository {
    private final JdbcTemplate jdbc;

    public InteractionLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(long deviceId, String inputText, String normalizedText, CommandType type,
                    String replyText, boolean success, String source) {
        jdbc.update("""
                INSERT INTO va_interaction_log(device_id, input_text, normalized_text, command_type,
                    reply_text, success, source, sfyx)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                """, deviceId, inputText, normalizedText, type.name(), replyText, success ? 1 : 0, source);
    }

    public List<InteractionLogView> list(String deviceCode, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbc.query("""
                SELECT l.id, d.device_code, l.input_text, l.reply_text, l.command_type,
                       l.success, l.created_at
                FROM va_interaction_log l JOIN va_device d ON d.id = l.device_id AND d.sfyx = 1
                WHERE l.sfyx = 1 AND d.device_code = ?
                ORDER BY l.id DESC LIMIT ?
                """, (rs, rowNum) -> new InteractionLogView(
                rs.getLong("id"), rs.getString("device_code"), rs.getString("input_text"),
                rs.getString("reply_text"), CommandType.valueOf(rs.getString("command_type")),
                rs.getBoolean("success"), rs.getTimestamp("created_at").toInstant().toEpochMilli()),
                deviceCode, safeLimit);
    }

    public boolean softDelete(long id) {
        return jdbc.update("UPDATE va_interaction_log SET sfyx = 0 WHERE id = ? AND sfyx = 1", id) > 0;
    }
}
