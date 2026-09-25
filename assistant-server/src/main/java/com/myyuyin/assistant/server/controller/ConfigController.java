package com.myyuyin.assistant.server.controller;

import com.myyuyin.assistant.common.ApiResponse;
import com.myyuyin.assistant.common.ConfigItem;
import com.myyuyin.assistant.server.repository.CommandRuleRecord;
import com.myyuyin.assistant.server.repository.CommandRuleRepository;
import com.myyuyin.assistant.server.repository.ConfigRepository;
import com.myyuyin.assistant.server.repository.WakeWordRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ConfigController {
    private final ConfigRepository configRepository;
    private final WakeWordRepository wakeWordRepository;
    private final CommandRuleRepository ruleRepository;

    public ConfigController(ConfigRepository configRepository, WakeWordRepository wakeWordRepository,
                            CommandRuleRepository ruleRepository) {
        this.configRepository = configRepository;
        this.wakeWordRepository = wakeWordRepository;
        this.ruleRepository = ruleRepository;
    }

    @GetMapping("/config")
    public ApiResponse<List<ConfigItem>> list() {
        return ApiResponse.ok(configRepository.listActive());
    }

    @PutMapping("/config/{key}")
    public ApiResponse<ConfigItem> update(@PathVariable String key, @RequestBody ConfigUpdateRequest body) {
        ConfigItem existing = configRepository.listActive().stream()
                .filter(item -> item.key().equals(key)).findFirst().orElse(null);
        if (existing != null && !existing.editable()) {
            return ApiResponse.error("该配置为只读配置");
        }
        ConfigItem item = new ConfigItem(key, body.value(),
                body.valueType() == null ? existing == null ? "STRING" : existing.valueType() : body.valueType(),
                body.description() == null ? existing == null ? null : existing.description() : body.description(),
                body.editable() == null ? existing == null || existing.editable() : body.editable());
        configRepository.upsert(item);
        return ApiResponse.ok(item);
    }

    @PostMapping("/config")
    public ApiResponse<ConfigItem> create(@RequestBody ConfigItem item) {
        configRepository.upsert(item);
        return ApiResponse.ok(item);
    }

    @DeleteMapping("/config/{key}")
    public ApiResponse<Boolean> delete(@PathVariable String key) {
        return ApiResponse.ok(configRepository.softDelete(key));
    }

    @GetMapping("/wake-words")
    public ApiResponse<List<String>> wakeWords() {
        return ApiResponse.ok(wakeWordRepository.listEnabled());
    }

    @PostMapping("/wake-words/{word}")
    public ApiResponse<List<String>> addWakeWord(@PathVariable String word) {
        wakeWordRepository.add(word);
        return ApiResponse.ok(wakeWordRepository.listEnabled());
    }

    @DeleteMapping("/wake-words/{word}")
    public ApiResponse<Boolean> deleteWakeWord(@PathVariable String word) {
        return ApiResponse.ok(wakeWordRepository.softDelete(word));
    }

    @GetMapping("/command-rules")
    public ApiResponse<List<CommandRuleRecord>> rules() {
        return ApiResponse.ok(ruleRepository.listAllActive());
    }

    @DeleteMapping("/command-rules/{id}")
    public ApiResponse<Boolean> deleteRule(@PathVariable long id) {
        return ApiResponse.ok(ruleRepository.softDelete(id));
    }

    public record ConfigUpdateRequest(String value, String valueType, String description, Boolean editable) {
    }
}
