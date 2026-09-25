package com.myyuyin.assistant.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AlarmRepository {
    private final JdbcTemplate jdbc;

    public AlarmRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AlarmRecord findActiveByDevice(long deviceId) {
        List<AlarmRecord> alarms = jdbc.query("""
                SELECT a.id, a.device_id, d.device_code, d.client_name, a.duration_minutes,
                       a.next_fire_at, a.status
                FROM va_alarm a JOIN va_device d ON d.id = a.device_id AND d.sfyx = 1
                WHERE a.device_id = ? AND a.sfyx = 1 AND a.status = 'ACTIVE'
                ORDER BY a.id DESC LIMIT 1
                """, (rs, rowNum) -> map(rs), deviceId);
        return alarms.isEmpty() ? null : alarms.getFirst();
    }

    public AlarmRecord findActiveByDeviceCode(String deviceCode) {
        List<AlarmRecord> alarms = jdbc.query("""
                SELECT a.id, a.device_id, d.device_code, d.client_name, a.duration_minutes,
                       a.next_fire_at, a.status
                FROM va_alarm a JOIN va_device d ON d.id = a.device_id AND d.sfyx = 1
                WHERE d.device_code = ? AND a.sfyx = 1 AND a.status = 'ACTIVE'
                ORDER BY a.id DESC LIMIT 1
                """, (rs, rowNum) -> map(rs), deviceCode);
        return alarms.isEmpty() ? null : alarms.getFirst();
    }

    @Transactional
    public synchronized AlarmRecord createActive(long deviceId, int durationMinutes) {
        Instant nextFireAt = Instant.now().plusSeconds(durationMinutes * 60L);
        jdbc.update("""
                INSERT INTO va_alarm(device_id, duration_minutes, next_fire_at, status, sfyx)
                VALUES (?, ?, ?, 'ACTIVE', 1)
                """, deviceId, durationMinutes, Timestamp.from(nextFireAt));
        Long id = jdbc.queryForObject("""
                SELECT id FROM va_alarm WHERE device_id = ? AND sfyx = 1
                ORDER BY id DESC LIMIT 1
                """, Long.class, deviceId);
        DeviceRecord device = jdbc.queryForObject("""
                SELECT id, device_code, client_name, status, state, last_seen_at
                FROM va_device WHERE id = ? AND sfyx = 1
                """, (rs, rowNum) -> new DeviceRecord(
                rs.getLong("id"), rs.getString("device_code"), rs.getString("client_name"),
                rs.getString("status"), rs.getString("state"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant().toEpochMilli()), deviceId);
        return new AlarmRecord(id, deviceId, device.deviceCode(), device.clientName(),
                durationMinutes, nextFireAt.toEpochMilli(), "ACTIVE");
    }

    public boolean cancelActive(long deviceId) {
        return jdbc.update("""
                UPDATE va_alarm SET status = 'CANCELLED', sfyx = 0
                WHERE device_id = ? AND sfyx = 1 AND status = 'ACTIVE'
                """, deviceId) > 0;
    }

    public List<AlarmRecord> listDue(int limit) {
        return jdbc.query("""
                SELECT a.id, a.device_id, d.device_code, d.client_name, a.duration_minutes,
                       a.next_fire_at, a.status
                FROM va_alarm a JOIN va_device d ON d.id = a.device_id AND d.sfyx = 1
                WHERE a.sfyx = 1 AND a.status = 'ACTIVE' AND a.next_fire_at <= ?
                ORDER BY a.next_fire_at ASC LIMIT ?
                """, (rs, rowNum) -> map(rs), Timestamp.from(Instant.now()), limit);
    }

    public boolean markFired(long id, long observedNextFireAt, long newNextFireAt) {
        return jdbc.update("""
                UPDATE va_alarm SET last_fired_at = ?, next_fire_at = ?
                WHERE id = ? AND sfyx = 1 AND status = 'ACTIVE' AND next_fire_at = ?
                """, Timestamp.from(Instant.now()), Timestamp.from(Instant.ofEpochMilli(newNextFireAt)),
                id, Timestamp.from(Instant.ofEpochMilli(observedNextFireAt))) == 1;
    }

    private AlarmRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AlarmRecord(rs.getLong("id"), rs.getLong("device_id"),
                rs.getString("device_code"), rs.getString("client_name"),
                rs.getInt("duration_minutes"), rs.getTimestamp("next_fire_at").toInstant().toEpochMilli(),
                rs.getString("status"));
    }
}
