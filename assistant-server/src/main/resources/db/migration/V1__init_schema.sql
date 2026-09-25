CREATE TABLE IF NOT EXISTS va_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    value_type VARCHAR(24) NOT NULL DEFAULT 'STRING',
    description VARCHAR(255) NULL,
    editable TINYINT(1) NOT NULL DEFAULT 1,
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_config_key_sfyx (config_key, sfyx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数配置';

CREATE TABLE IF NOT EXISTS va_command_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    command_type VARCHAR(32) NOT NULL,
    match_type VARCHAR(16) NOT NULL,
    pattern_text VARCHAR(512) NOT NULL,
    response_template TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_rule_type_sfyx (command_type, sfyx, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='命令与回复模板';

CREATE TABLE IF NOT EXISTS va_wake_word (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    word VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_wake_word_sfyx (word, sfyx, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='语音唤醒词';

CREATE TABLE IF NOT EXISTS va_device (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_code VARCHAR(64) NOT NULL,
    client_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(32) NOT NULL DEFAULT 'CLIENT',
    status VARCHAR(24) NOT NULL DEFAULT 'OFFLINE',
    state VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    last_seen_at DATETIME(3) NULL,
    params_json JSON NULL,
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_device_code_sfyx (device_code, sfyx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端设备';

CREATE TABLE IF NOT EXISTS va_runtime_status (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id BIGINT UNSIGNED NOT NULL,
    client_version VARCHAR(64) NULL,
    cpu_load DOUBLE NULL,
    memory_used_mb DOUBLE NULL,
    network_latency_ms INT NULL,
    status_json JSON NULL,
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_runtime_device_sfyx (device_id, sfyx, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端运行状态';

CREATE TABLE IF NOT EXISTS va_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id BIGINT UNSIGNED NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    expires_at DATETIME(3) NULL,
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_session_device_sfyx (device_id, sfyx)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话状态';

CREATE TABLE IF NOT EXISTS va_alarm (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id BIGINT UNSIGNED NOT NULL,
    duration_minutes INT NOT NULL,
    next_fire_at DATETIME(3) NOT NULL,
    last_fired_at DATETIME(3) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_alarm_due_sfyx (status, sfyx, next_fire_at),
    KEY idx_alarm_device_sfyx (device_id, sfyx, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='循环闹钟';

CREATE TABLE IF NOT EXISTS va_interaction_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id BIGINT UNSIGNED NOT NULL,
    input_text TEXT NULL,
    normalized_text TEXT NULL,
    command_type VARCHAR(32) NOT NULL,
    reply_text TEXT NULL,
    success TINYINT(1) NOT NULL DEFAULT 1,
    source VARCHAR(24) NOT NULL DEFAULT 'VOICE',
    sfyx TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_log_device_sfyx (device_id, sfyx, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='语音交互日志';

INSERT INTO va_config (config_key, config_value, value_type, description, editable, sfyx) VALUES
('wake.word', '三角洲', 'STRING', '默认语音唤醒词', 1, 1),
('command.require_wake_word', 'true', 'BOOLEAN', '普通命令是否必须经过唤醒', 1, 1),
('session.wait_ms', '15000', 'INTEGER', '唤醒后等待命令的毫秒数', 1, 1),
('stt.model.path', '/opt/vosk-model-small-cn', 'STRING', 'Vosk 中文模型目录', 0, 1),
('stt.sample_rate', '16000', 'INTEGER', '语音识别采样率', 0, 1),
('tts.voice', 'zh', 'STRING', 'eSpeak NG 音色', 1, 1),
('tts.speed', '160', 'INTEGER', 'eSpeak NG 语速', 1, 1),
('tts.amplitude', '180', 'INTEGER', 'eSpeak NG 音量', 1, 1),
('timezone', 'Asia/Shanghai', 'STRING', '时间和闹钟使用的时区', 1, 1),
('unknown.reply.enabled', 'true', 'BOOLEAN', '未识别命令时是否回复', 1, 1),
('unknown.reply', '我没听清，请再说一遍', 'STRING', '未识别命令时的回复', 1, 1),
('client.default.device_code', 'client-001', 'STRING', '默认客户端设备编号', 1, 1),
('client.audio.threshold', '700', 'INTEGER', '客户端连续监听语音阈值', 1, 1),
('client.audio.silence_ms', '900', 'INTEGER', '客户端静音结束判定毫秒数', 1, 1),
('client.audio.min_speech_ms', '350', 'INTEGER', '客户端最短有效语音毫秒数', 1, 1),
('client.audio.max_record_ms', '10000', 'INTEGER', '客户端单次录音最大毫秒数', 1, 1),

('alarm.max_minutes', '1440', 'INTEGER', '单次循环闹钟最大分钟数', 1, 1),
('alarm.none.reply', '当前没有运行中的闹钟', 'STRING', '没有可查询或取消的闹钟时的回复', 1, 1),
('stt.language', 'zh-CN', 'STRING', '语音识别语言标识', 0, 1),
('tts.executable', 'espeak-ng', 'STRING', '服务端语音合成程序', 0, 1),
('device.online_timeout_ms', '90000', 'INTEGER', '客户端在线状态超时毫秒数', 0, 1),
('audio.server.playback.enabled', 'false', 'BOOLEAN', '服务端所在主机是否直接播放音频', 1, 1);

INSERT INTO va_command_rule (command_type, match_type, pattern_text, response_template, priority, enabled, sfyx) VALUES
('WAKE', 'ALIAS', '三角洲', '我在', 1000, 1, 1),
('TIME', 'ALIAS', '几点了', '现在{hour}点{minute}分，重复现在{hour}点{minute}分。{weekday}', 100, 1, 1),
('TIME', 'ALIAS', '现在几点', '现在{hour}点{minute}分，重复现在{hour}点{minute}分。{weekday}', 100, 1, 1),
('TIME', 'ALIAS', '查询时间', '现在{hour}点{minute}分，重复现在{hour}点{minute}分。{weekday}', 100, 1, 1),
('TIME', 'ALIAS', '时间', '现在{hour}点{minute}分，重复现在{hour}点{minute}分。{weekday}', 90, 1, 1),
('ALARM_CREATE', 'REGEX', '.*?([0-9一二三四五六七八九十百零两]+)(分钟|小时).*闹钟.*', '{duration}分钟闹钟已设置。', 100, 1, 1),
('ALARM_CREATE', 'REGEX', '.*?(?:定|设置|创建)([0-9一二三四五六七八九十百零两]+)(分钟|小时).*', '{duration}分钟闹钟已设置。', 95, 1, 1),
('ALARM_CONFLICT', 'ALIAS', '已有闹钟', '已有{duration}分钟闹正在运行，请先取消。', 100, 1, 1),
('ALARM_REMAIN', 'ALIAS', '还剩多久', '还剩{remaining}分钟，重复还剩{remaining}分钟', 100, 1, 1),
('ALARM_REMAIN', 'ALIAS', '剩余时间', '还剩{remaining}分钟，重复还剩{remaining}分钟', 100, 1, 1),
('ALARM_REMAIN', 'ALIAS', '还有多久', '还剩{remaining}分钟，重复还剩{remaining}分钟', 100, 1, 1),
('ALARM_CANCEL', 'ALIAS', '取消闹钟', '闹钟已取消', 100, 1, 1),
('ALARM_CANCEL', 'ALIAS', '关闭闹钟', '闹钟已取消', 100, 1, 1),
('ALARM_FIRE', 'SYSTEM', 'SYSTEM_ALARM', '{duration}分钟循环闹钟时间到了。', 100, 1, 1);

INSERT INTO va_wake_word (word, enabled, sfyx) VALUES ('三角洲', 1, 1);
