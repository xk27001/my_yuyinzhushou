INSERT INTO va_config (config_key, config_value, value_type, description, editable, sfyx)
SELECT 'alarm.max_minutes', '1440', 'INTEGER', '单次循环闹钟最大分钟数', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM va_config WHERE config_key = 'alarm.max_minutes' AND sfyx = 1);

INSERT INTO va_config (config_key, config_value, value_type, description, editable, sfyx)
SELECT 'alarm.none.reply', '当前没有运行中的闹钟', 'STRING', '没有可查询或取消的闹钟时的回复', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM va_config WHERE config_key = 'alarm.none.reply' AND sfyx = 1);

INSERT INTO va_config (config_key, config_value, value_type, description, editable, sfyx)
SELECT 'stt.language', 'zh-CN', 'STRING', '语音识别语言标识', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM va_config WHERE config_key = 'stt.language' AND sfyx = 1);

INSERT INTO va_config (config_key, config_value, value_type, description, editable, sfyx)
SELECT 'tts.executable', 'espeak-ng', 'STRING', '服务端语音合成程序', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM va_config WHERE config_key = 'tts.executable' AND sfyx = 1);
