package com.myyuyin.assistant.server.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlarmScheduler {
    private final AlarmService alarmService;

    public AlarmScheduler(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @Scheduled(fixedDelayString = "${assistant.alarm.scan-interval-ms:1000}")
    public void scan() {
        alarmService.processDueAlarms();
    }
}
