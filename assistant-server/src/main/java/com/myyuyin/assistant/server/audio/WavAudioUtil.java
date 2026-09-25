package com.myyuyin.assistant.server.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class WavAudioUtil {
    private WavAudioUtil() {
    }

    public static WavAudio parse(byte[] wav) {
        if (wav == null || wav.length < 44) {
            throw new IllegalArgumentException("音频文件过小，不是有效的 WAV 文件");
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(0) != 0x46464952 || buffer.getInt(8) != 0x45564157) {
            throw new IllegalArgumentException("只支持 RIFF/WAVE 格式音频");
        }
        int offset = 12;
        int sampleRate = 0;
        int channels = 0;
        int bits = 0;
        byte[] pcm = null;
        while (offset + 8 <= wav.length) {
            int chunkId = buffer.getInt(offset);
            int chunkSize = buffer.getInt(offset + 4);
            int contentStart = offset + 8;
            if (chunkSize < 0 || contentStart + chunkSize > wav.length) {
                throw new IllegalArgumentException("WAV 数据块长度无效");
            }
            if (chunkId == 0x20746D66 && chunkSize >= 16) {
                int format = Short.toUnsignedInt(buffer.getShort(contentStart));
                channels = Short.toUnsignedInt(buffer.getShort(contentStart + 2));
                sampleRate = buffer.getInt(contentStart + 4);
                bits = Short.toUnsignedInt(buffer.getShort(contentStart + 14));
                if (format != 1 && format != 0xFFFE) {
                    throw new IllegalArgumentException("仅支持未压缩 PCM WAV");
                }
            } else if (chunkId == 0x61746164) {
                pcm = Arrays.copyOfRange(wav, contentStart, contentStart + chunkSize);
            }
            offset = contentStart + chunkSize + (chunkSize % 2);
        }
        if (pcm == null || sampleRate <= 0 || channels <= 0 || bits <= 0) {
            throw new IllegalArgumentException("WAV 文件缺少 fmt 或 data 数据块");
        }
        return new WavAudio(sampleRate, channels, bits, pcm);
    }
}
