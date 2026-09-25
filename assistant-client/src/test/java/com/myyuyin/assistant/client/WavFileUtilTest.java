package com.myyuyin.assistant.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WavFileUtilTest {
    @Test
    void writesValidPcmWaveHeader() {
        byte[] pcm = new byte[]{1, 0, 2, 0};
        byte[] wav = WavFileUtil.toWav(pcm, 16000, 1, 16);
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x46464952, buffer.getInt(0));
        assertEquals(0x45564157, buffer.getInt(8));
        assertEquals(16000, buffer.getInt(24));
        assertEquals(pcm.length, buffer.getInt(40));
        assertEquals(4, wav.length - 44);
    }
}
