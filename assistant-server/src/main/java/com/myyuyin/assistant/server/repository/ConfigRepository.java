package com.myyuyin.assistant.server.repository;

import com.myyuyin.assistant.common.ConfigItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class ConfigRepository {
    private final JdbcTemplate jdbc;

    public ConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConfigItem> listActive() {
        return jdbc.query("""
                SELECT config_key, config_value, value_type, description, editable
                FROM va_config WHERE sfyx = 1 ORDER BY config_key
                """, (rs, rowNum) -> new ConfigItem(
                rs.getString("config_key"), rs.getString("config_value"),
                rs.getString("value_type"), rs.getString("description"),
                rs.getBoolean("editable")));
    }

    public String getString(String key, String defaultValue) {
        List<String> values = jdbc.query("""
                SELECT config_value FROM va_config
                WHERE config_key = ? AND sfyx = 1 LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), key);
        return values.isEmpty() ? defaultValue : values.getFirst();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, Boolean.toString(defaultValue)));
    }

    @Transactional
    public void upsert(ConfigItem item) {
        int updated = jdbc.update("""
                UPDATE va_config SET config_value = ?, value_type = ?, description = ?,
                    editable = ?, sfyx = 1
                WHERE config_key = ? AND sfyx = 1
                """, item.value(), item.valueType(), item.description(), item.editable(), item.key());
        if (updated == 0) {
            int restored = jdbc.update("""
                    UPDATE va_config SET config_value = ?, value_type = ?, description = ?,
                        editable = ?, sfyx = 1
                    WHERE config_key = ? AND sfyx = 0
                    """, item.value(), item.valueType(), item.description(), item.editable(), item.key());
            if (restored == 0) {
                jdbc.update("""
                        INSERT INTO va_config(config_key, config_value, value_type, description, editable, sfyx)
                        VALUES (?, ?, ?, ?, ?, 1)
                        """, item.key(), item.value(), item.valueType(), item.description(), item.editable());
            }
        }
    }

    @Transactional
    public boolean softDelete(String key) {
        return jdbc.update("UPDATE va_config SET sfyx = 0 WHERE config_key = ? AND sfyx = 1", key) > 0;
    }
}
