package com.myyuyin.assistant.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WakeWordRepository {
    private final JdbcTemplate jdbc;

    public WakeWordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> listEnabled() {
        return jdbc.query("""
                SELECT word FROM va_wake_word WHERE sfyx = 1 AND enabled = 1 ORDER BY id
                """, (rs, rowNum) -> rs.getString(1));
    }

    public void add(String word) {
        List<Long> deleted = jdbc.query("""
                SELECT id FROM va_wake_word WHERE word = ? AND sfyx = 0 ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getLong(1), word);
        if (deleted.isEmpty()) {
            jdbc.update("INSERT INTO va_wake_word(word, enabled, sfyx) VALUES (?, 1, 1)", word);
        } else {
            jdbc.update("UPDATE va_wake_word SET enabled = 1, sfyx = 1 WHERE id = ?", deleted.getFirst());
        }
    }

    public boolean softDelete(String word) {
        return jdbc.update("""
                UPDATE va_wake_word SET sfyx = 0, enabled = 0 WHERE word = ? AND sfyx = 1
                """, word) > 0;
    }
}
