package com.myyuyin.assistant.server.audio;

import com.myyuyin.assistant.server.repository.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;

@Component
public class ServerAudioPlayer {
    private static final Logger log = LoggerFactory.getLogger(ServerAudioPlayer.class);
    private final ConfigRepository configRepository;

    public ServerAudioPlayer(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public void playIfEnabled(byte[] wavBytes) {
        if (wavBytes == null || wavBytes.length == 0
                || !configRepository.getBoolean("audio.server.playback.enabled", false)
                || GraphicsEnvironment.isHeadless()) {
            return;
        }
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes))) {
            Clip clip = AudioSystem.getClip();
            clip.open(input);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception ex) {
            log.warn("服务端直接播放音频失败: {}", ex.getMessage());
        }
    }
}
