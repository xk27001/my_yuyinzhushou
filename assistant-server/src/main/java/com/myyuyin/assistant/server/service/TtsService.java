package com.myyuyin.assistant.server.service;

import com.myyuyin.assistant.server.repository.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TtsService {
    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private final ConfigRepository configRepository;
    private final String configuredExecutable;
    private final Path configuredOutputDirectory;

    public TtsService(ConfigRepository configRepository,
                      @Value("${assistant.tts.executable:espeak-ng}") String configuredExecutable,
                      @Value("${assistant.tts.output-directory}") String configuredOutputDirectory) {
        this.configRepository = configRepository;
        this.configuredExecutable = configuredExecutable;
        this.configuredOutputDirectory = Path.of(configuredOutputDirectory);
    }

    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String executable = configRepository.getString("tts.executable", configuredExecutable);
        String voice = configRepository.getString("tts.voice", "zh");
        int speed = configRepository.getInt("tts.speed", 160);
        int amplitude = configRepository.getInt("tts.amplitude", 180);
        Path output = null;
        try {
            Files.createDirectories(configuredOutputDirectory);
            output = configuredOutputDirectory.resolve("speech-" + UUID.randomUUID() + ".wav");
            Process process = new ProcessBuilder(executable, "-v", voice, "-s", Integer.toString(speed),
                    "-a", Integer.toString(amplitude), "-w", output.toString(), text)
                    .redirectErrorStream(true)
                    .start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("语音合成超时");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("eSpeak NG 返回错误码 " + process.exitValue() + ": " + processOutput);
            }
            return Files.readAllBytes(output);
        } catch (IOException ex) {
            log.warn("语音合成不可用: {}", ex.getMessage());
            return new byte[0];
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new byte[0];
        } catch (RuntimeException ex) {
            log.warn("语音合成失败: {}", ex.getMessage());
            return new byte[0];
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // Temporary audio is cleaned by the operating system when possible.
                }
            }
        }
    }
}
