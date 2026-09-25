package com.myyuyin.assistant.server.service;

import com.myyuyin.assistant.common.AlarmView;
import com.myyuyin.assistant.common.CommandType;
import com.myyuyin.assistant.common.WebSocketEvent;
import com.myyuyin.assistant.server.audio.ServerAudioPlayer;
import com.myyuyin.assistant.server.repository.*;
import com.myyuyin.assistant.server.websocket.DeviceWebSocketHandler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AlarmService {
    private final AlarmRepository alarmRepository;
    private final DeviceRepository deviceRepository;
    private final CommandRuleRepository ruleRepository;
    private final CommandParserService parserService;
    private final InteractionLogRepository logRepository;
    private final TtsService ttsService;
    private final DeviceWebSocketHandler webSocketHandler;
    private final ServerAudioPlayer serverAudioPlayer;

    public AlarmService(AlarmRepository alarmRepository, DeviceRepository deviceRepository,
                        CommandRuleRepository ruleRepository, CommandParserService parserService,
                        InteractionLogRepository logRepository, TtsService ttsService,
                        DeviceWebSocketHandler webSocketHandler,
                        ServerAudioPlayer serverAudioPlayer) {
        this.alarmRepository = alarmRepository;
        this.deviceRepository = deviceRepository;
        this.ruleRepository = ruleRepository;
        this.parserService = parserService;
        this.logRepository = logRepository;
        this.ttsService = ttsService;
        this.webSocketHandler = webSocketHandler;
        this.serverAudioPlayer = serverAudioPlayer;
    }

    public AlarmView active(String deviceCode) {
        AlarmRecord alarm = alarmRepository.findActiveByDeviceCode(deviceCode);
        return alarm == null ? null : toView(alarm);
    }

    public boolean cancel(String deviceCode) {
        List<DeviceRecord> devices = deviceRepository.findActive(deviceCode);
        return !devices.isEmpty() && alarmRepository.cancelActive(devices.getFirst().id());
    }

    public void processDueAlarms() {
        for (AlarmRecord alarm : alarmRepository.listDue(100)) {
            Instant now = Instant.now();
            Instant next = nextFire(Instant.ofEpochMilli(alarm.nextFireAt()), alarm.durationMinutes(), now);
            if (!alarmRepository.markFired(alarm.id(), alarm.nextFireAt(), next.toEpochMilli())) {
                continue;
            }
            CommandRuleRecord rule = ruleRepository.findEnabledByType(CommandType.ALARM_FIRE.name());
            String template = rule == null ? "{duration}分钟循环闹钟时间到了。" : rule.responseTemplate();
            String reply = parserService.render(template, Map.of("duration", alarm.durationMinutes()));
            byte[] wav = ttsService.synthesize(reply);
            serverAudioPlayer.playIfEnabled(wav);
            String audio = wav.length == 0 ? null : Base64.getEncoder().encodeToString(wav);
            WebSocketEvent event = new WebSocketEvent("ALARM_FIRE", alarm.deviceCode(), reply,
                    audio, System.currentTimeMillis());
            webSocketHandler.send(alarm.deviceCode(), event);
            logRepository.add(alarm.deviceId(), null, null, CommandType.ALARM_FIRE,
                    reply, true, "ALARM");
        }
    }

    private Instant nextFire(Instant observed, int durationMinutes, Instant now) {
        Instant next = observed.plus(durationMinutes, ChronoUnit.MINUTES);
        while (!next.isAfter(now)) {
            next = next.plus(durationMinutes, ChronoUnit.MINUTES);
        }
        return next;
    }

    private AlarmView toView(AlarmRecord alarm) {
        return new AlarmView(alarm.id(), alarm.deviceCode(), alarm.durationMinutes(),
                alarm.nextFireAt(), alarm.status());
    }
}
