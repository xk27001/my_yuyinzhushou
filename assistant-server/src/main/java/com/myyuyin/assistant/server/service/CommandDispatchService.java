package com.myyuyin.assistant.server.service;

import com.myyuyin.assistant.common.CommandType;
import com.myyuyin.assistant.common.VoiceProcessResponse;
import com.myyuyin.assistant.server.audio.ServerAudioPlayer;
import com.myyuyin.assistant.server.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class CommandDispatchService {
    private static final Logger log = LoggerFactory.getLogger(CommandDispatchService.class);

    private final DeviceRepository deviceRepository;
    private final WakeWordRepository wakeWordRepository;
    private final SessionRepository sessionRepository;
    private final AlarmRepository alarmRepository;
    private final CommandRuleRepository ruleRepository;
    private final ConfigRepository configRepository;
    private final InteractionLogRepository logRepository;
    private final CommandParserService parserService;
    private final TtsService ttsService;
    private final ServerAudioPlayer serverAudioPlayer;

    public CommandDispatchService(DeviceRepository deviceRepository,
                                  WakeWordRepository wakeWordRepository,
                                  SessionRepository sessionRepository,
                                  AlarmRepository alarmRepository,
                                  CommandRuleRepository ruleRepository,
                                  ConfigRepository configRepository,
                                  InteractionLogRepository logRepository,
                                  CommandParserService parserService,
                                  TtsService ttsService,
                                  ServerAudioPlayer serverAudioPlayer) {
        this.deviceRepository = deviceRepository;
        this.wakeWordRepository = wakeWordRepository;
        this.sessionRepository = sessionRepository;
        this.alarmRepository = alarmRepository;
        this.ruleRepository = ruleRepository;
        this.configRepository = configRepository;
        this.logRepository = logRepository;
        this.parserService = parserService;
        this.ttsService = ttsService;
        this.serverAudioPlayer = serverAudioPlayer;
    }

    public VoiceProcessResponse processText(String deviceCode, String clientName, String text, String source) {
        String safeCode = normalizeDeviceCode(deviceCode);
        String safeText = text == null ? "" : text.trim();
        synchronized (("device:" + safeCode).intern()) {
            DeviceRecord device = deviceRepository.findOrCreate(safeCode, clientName);
            String normalized = TextNormalizer.normalize(safeText);
            WakeDetection wake = detectWake(normalized);
            boolean ignored = false;

            if (wake.detected() && wake.remainder().isBlank()) {
                sessionRepository.setAwaiting(device.id(), configRepository.getInt("session.wait_ms", 15000));
                deviceRepository.touch(device.id(), "AWAITING_COMMAND");
                String replyText = wakeTemplate();
                String audio = synthesize(replyText);
                logRepository.add(device.id(), safeText, normalized, CommandType.WAKE, replyText, true, source);
                return new VoiceProcessResponse(safeCode, safeText, replyText, CommandType.WAKE,
                        false, audio, audio == null ? null : "audio/wav", System.currentTimeMillis());
            }

            String commandText = wake.detected() ? wake.remainder() : normalized;
            if (!wake.detected() && configRepository.getBoolean("command.require_wake_word", true)
                    && !sessionRepository.isAwaiting(device.id())) {
                ignored = true;
            }
            if (wake.detected()) {
                sessionRepository.clear(device.id());
            }

            if (ignored || commandText.isBlank()) {
                logRepository.add(device.id(), safeText, normalized, CommandType.IGNORED, null, true, source);
                return new VoiceProcessResponse(safeCode, safeText, null, CommandType.IGNORED,
                        true, null, null, System.currentTimeMillis());
            }

            ParsedCommand parsed = parserService.parse(commandText);
            CommandReply reply = execute(device, parsed);
            sessionRepository.clear(device.id());
            deviceRepository.touch(device.id(), reply.ignored() ? "IDLE" : "REPLYING");
            String audio = synthesize(reply.text());
            logRepository.add(device.id(), safeText, normalized, reply.type(), reply.text(), true, source);
            return new VoiceProcessResponse(safeCode, safeText, reply.text(), reply.type(),
                    reply.ignored(), audio, audio == null ? null : "audio/wav", System.currentTimeMillis());
        }
    }

    private CommandReply execute(DeviceRecord device, ParsedCommand parsed) {
        if (parsed == null) {
            return unknown();
        }
        return switch (parsed.type()) {
            case TIME -> time(parsed);
            case ALARM_CREATE -> createAlarm(device, parsed);
            case ALARM_REMAIN -> remaining(device, parsed);
            case ALARM_CANCEL -> cancel(device, parsed);
            default -> unknown();
        };
    }

    private CommandReply time(ParsedCommand parsed) {
        ZoneId zone = ZoneId.of(configRepository.getString("timezone", "Asia/Shanghai"));
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("hour", now.getHour());
        values.put("minute", now.getMinute());
        values.put("weekday", chineseWeekday(now.getDayOfWeek()));
        return new CommandReply(CommandType.TIME,
                parserService.render(parsed.rule().responseTemplate(), values), false);
    }

    private CommandReply createAlarm(DeviceRecord device, ParsedCommand parsed) {
        int minutes = parserService.extractDurationMinutes(parsed);
        int maxMinutes = configRepository.getInt("alarm.max_minutes", 1440);
        if (minutes <= 0 || minutes > maxMinutes) {
            return new CommandReply(CommandType.UNKNOWN,
                    "闹钟时长必须在1到" + maxMinutes + "分钟之间", false);
        }
        AlarmRecord active = alarmRepository.findActiveByDevice(device.id());
        if (active != null) {
            CommandRuleRecord rule = ruleRepository.findEnabledByType(CommandType.ALARM_CONFLICT.name());
            String template = rule == null ? "已有{duration}分钟闹正在运行，请先取消。" : rule.responseTemplate();
            return new CommandReply(CommandType.ALARM_CONFLICT,
                    parserService.render(template, Map.of("duration", active.durationMinutes())), false);
        }
        alarmRepository.createActive(device.id(), minutes);
        return new CommandReply(CommandType.ALARM_CREATE,
                parserService.render(parsed.rule().responseTemplate(), Map.of("duration", minutes)), false);
    }

    private CommandReply remaining(DeviceRecord device, ParsedCommand parsed) {
        AlarmRecord active = alarmRepository.findActiveByDevice(device.id());
        if (active == null) {
            return noAlarm();
        }
        long remainingMillis = active.nextFireAt() - System.currentTimeMillis();
        int remainingMinutes = Math.max(1, (int) Math.ceil(remainingMillis / 60000.0));
        return new CommandReply(CommandType.ALARM_REMAIN,
                parserService.render(parsed.rule().responseTemplate(),
                        Map.of("remaining", remainingMinutes)), false);
    }

    private CommandReply cancel(DeviceRecord device, ParsedCommand parsed) {
        boolean cancelled = alarmRepository.cancelActive(device.id());
        if (!cancelled) {
            return noAlarm();
        }
        return new CommandReply(CommandType.ALARM_CANCEL, parsed.rule().responseTemplate(), false);
    }

    private CommandReply noAlarm() {
        return new CommandReply(CommandType.UNKNOWN,
                configRepository.getString("alarm.none.reply", "当前没有运行中的闹钟"), false);
    }

    private CommandReply unknown() {
        if (!configRepository.getBoolean("unknown.reply.enabled", true)) {
            return new CommandReply(CommandType.IGNORED, null, true);
        }
        return new CommandReply(CommandType.UNKNOWN,
                configRepository.getString("unknown.reply", "我没听清，请再说一遍"), false);
    }

    private WakeDetection detectWake(String normalized) {
        List<String> words = new ArrayList<>(wakeWordRepository.listEnabled());
        String configured = configRepository.getString("wake.word", "三角洲");
        if (!words.contains(configured)) {
            words.add(configured);
        }
        for (String word : words) {
            String normalizedWord = TextNormalizer.normalize(word);
            int index = normalized.indexOf(normalizedWord);
            if (!normalizedWord.isBlank() && index >= 0) {
                return new WakeDetection(true,
                        normalized.substring(0, index) + normalized.substring(index + normalizedWord.length()));
            }
        }
        return new WakeDetection(false, normalized);
    }

    private String wakeTemplate() {
        CommandRuleRecord rule = ruleRepository.findEnabledByType(CommandType.WAKE.name());
        return rule == null ? "我在" : rule.responseTemplate();
    }

    private String synthesize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        byte[] wav = ttsService.synthesize(text);
        if (wav.length == 0) {
            return null;
        }
        serverAudioPlayer.playIfEnabled(wav);
        return Base64.getEncoder().encodeToString(wav);
    }

    private String chineseWeekday(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private String normalizeDeviceCode(String deviceCode) {
        if (deviceCode == null || !deviceCode.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("deviceCode 只能包含字母、数字、下划线和短横线");
        }
        return deviceCode;
    }

    private record WakeDetection(boolean detected, String remainder) {
    }

    private record CommandReply(CommandType type, String text, boolean ignored) {
    }
}
