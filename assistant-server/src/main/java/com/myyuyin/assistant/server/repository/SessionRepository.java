package com.myyuyin.assistant.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbc;

    public SessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized void setAwaiting(long deviceId, long waitMillis) {
        Instant expiresAt = Instant.now().plusMillis(waitMillis);
        int updated = jdbc.update("""
                UPDATE va_session SET state = 'AWAITING_COMMAND', expires_at = ?
                WHERE device_id = ? AND sfyx = 1
                """, Timestamp.from(expiresAt), deviceId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO va_session(device_id, state, expires_at, sfyx)
                    VALUES (?, 'AWAITING_COMMAND', ?, 1)
                    """, deviceId, Timestamp.from(expiresAt));
        }
    }

    public boolean isAwaiting(long deviceId) {
        List<String> states = jdbc.query("""
                SELECT state FROM va_session
                WHERE device_id = ? AND sfyx = 1 AND expires_at > ? LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), deviceId, Timestamp.from(Instant.now()));
        return !states.isEmpty() && "AWAITING_COMMAND".equals(states.getFirst());
    }

    public void clear(long deviceId) {
        jdbc.update("""
                UPDATE va_session SET state = 'IDLE', expires_at = NULL, sfyx = 0
                WHERE device_id = ? AND sfyx = 1
                """, deviceId);
    }
}
