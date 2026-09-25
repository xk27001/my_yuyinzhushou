package com.myyuyin.assistant.server.controller;

import com.myyuyin.assistant.common.AlarmView;
import com.myyuyin.assistant.common.ApiResponse;
import com.myyuyin.assistant.server.service.AlarmService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {
    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping("/{deviceCode}/active")
    public ApiResponse<AlarmView> active(@PathVariable String deviceCode) {
        return ApiResponse.ok(alarmService.active(deviceCode));
    }

    @DeleteMapping("/{deviceCode}/active")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable String deviceCode) {
        boolean cancelled = alarmService.cancel(deviceCode);
        return ApiResponse.ok(Map.of("cancelled", cancelled,
                "replyText", cancelled ? "闹钟已取消" : "当前没有运行中的闹钟"));
    }
}
