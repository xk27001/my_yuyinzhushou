package com.myyuyin.assistant.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChineseNumberParserTest {
    @Test
    void parsesArabicChineseAndCommonSpokenNumbers() {
        assertEquals(30, ChineseNumberParser.parse("30"));
        assertEquals(30, ChineseNumberParser.parse("三十"));
        assertEquals(15, ChineseNumberParser.parse("十五"));
        assertEquals(120, ChineseNumberParser.parse("一百二十"));
        assertEquals(2, ChineseNumberParser.parse("两"));
    }
}
