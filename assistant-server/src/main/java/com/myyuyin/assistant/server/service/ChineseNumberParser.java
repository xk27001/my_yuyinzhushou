package com.myyuyin.assistant.server.service;

import java.util.Map;

public final class ChineseNumberParser {
    private static final Map<Character, Integer> DIGITS = Map.ofEntries(
            Map.entry('零', 0), Map.entry('〇', 0), Map.entry('一', 1), Map.entry('二', 2),
            Map.entry('两', 2), Map.entry('三', 3), Map.entry('四', 4), Map.entry('五', 5),
            Map.entry('六', 6), Map.entry('七', 7), Map.entry('八', 8), Map.entry('九', 9));
    private static final Map<Character, Integer> UNITS = Map.of(
            '十', 10, '百', 100, '千', 1000, '万', 10000);

    private ChineseNumberParser() {
    }

    public static int parse(String value) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException("数字为空");
        }
        String text = value.trim();
        if (text.matches("[0-9]+")) {
            return Integer.parseInt(text);
        }
        int result = 0;
        int section = 0;
        int number = 0;
        for (char ch : text.toCharArray()) {
            Integer digit = DIGITS.get(ch);
            if (digit != null) {
                number = digit;
                continue;
            }
            Integer unit = UNITS.get(ch);
            if (unit == null) {
                continue;
            }
            if (unit == 10000) {
                section = (section + number) * unit;
                result += section;
                section = 0;
                number = 0;
            } else {
                if (number == 0) {
                    number = 1;
                }
                section += number * unit;
                number = 0;
            }
        }
        return result + section + number;
    }
}
