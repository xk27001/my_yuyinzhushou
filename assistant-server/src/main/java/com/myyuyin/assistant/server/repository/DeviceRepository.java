package com.myyuyin.assistant.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class DeviceRepository {
    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public DeviceRecord findOrCreate(String deviceCode, String clientName) {
        List<DeviceRecord> active = findActive(deviceCode);
        Instant now = Instant.now();
        if (!active.isEmpty()) {
            DeviceRecord device = active.getFirst();
            String name = safeClientName(clientName);
            jdbc.update("""
                    UPDATE va_device SET client_name = ?, status = 'ONLINE', last_seen_at = ?
                    WHERE id = ? AND sfyx = 1
                    """, name, Timestamp.from(now), device.id());
            return new DeviceRecord(device.id(), device.deviceCode(), name, "ONLINE",
                    device.state(), now.toEpochMilli());
        }
        List<Long> deleted = jdbc.query("""
                SELECT id FROM va_device WHERE device_code = ? AND sfyx = 0 ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getLong(1), deviceCode);
        String name = safeClientName(clientName);
        if (!deleted.isEmpty()) {
            long id = deleted.getFirst();
            jdbc.update("""
                    UPDATE va_device SET client_name = ?, status = 'ONLINE', state = 'IDLE',
                        last_seen_at = ?, sfyx = 1 WHERE id = ?
                    """, name, Timestamp.from(now), id);
            return new DeviceRecord(id, deviceCode, name, "ONLINE", "IDLE", now.toEpochMilli());
        }
        jdbc.update("""
                INSERT INTO va_device(device_code, client_name, status, state, last_seen_at, sfyx)
                VALUES (?, ?, 'ONLINE', 'IDLE', ?, 1)
                """, deviceCode, name, Timestamp.from(now));
        Long id = jdbc.queryForObject("""
                SELECT id FROM va_device WHERE device_code = ? AND sfyx = 1 ORDER BY id DESC LIMIT 1
                """, Long.class, deviceCode);
        return new DeviceRecord(id, deviceCode, name, "ONLINE", "IDLE", now.toEpochMilli());
    }

    public List<DeviceRecord> findActive(String deviceCode) {
        return jdbc.query("""
                SELECT id, device_code, client_name, status, state, last_seen_at
                FROM va_device WHERE device_code = ? AND sfyx = 1 ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> new DeviceRecord(
                rs.getLong("id"), rs.getString("device_code"), rs.getString("client_name"),
                rs.getString("status"), rs.getString("state"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant().toEpochMilli()),
                deviceCode);
    }

    public DeviceRecord findById(long id) {
        return jdbc.queryForObject("""
                SELECT id, device_code, client_name, status, state, last_seen_at
                FROM va_device WHERE id = ? AND sfyx = 1
                """, (rs, rowNum) -> new DeviceRecord(
                rs.getLong("id"), rs.getString("device_code"), rs.getString("client_name"),
                rs.getString("status"), rs.getString("state"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant().toEpochMilli()), id);
    }

    public void touch(long deviceId, String state) {
        jdbc.update("""
                UPDATE va_device SET status = 'ONLINE', state = ?, last_seen_at = ?
                WHERE id = ? AND sfyx = 1
                """, state, Timestamp.from(Instant.now()), deviceId);
    }

    public void markOffline(String deviceCode) {
        jdbc.update("""
                UPDATE va_device SET status = 'OFFLINE'
                WHERE device_code = ? AND sfyx = 1
                """, deviceCode);
    }

    public boolean softDelete(String deviceCode) {
        return jdbc.update("""
                UPDATE va_device SET status = 'DELETED', sfyx = 0
                WHERE device_code = ? AND sfyx = 1
                """, deviceCode) > 0;
    }

    private String safeClientName(String clientName) {
        return clientName == null || clientName.isBlank() ? "语音助手客户端" : clientName.trim();
    }
}
