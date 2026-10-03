package com.example.hk.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** 全系统统一用 Asia/Shanghai 解释预约字符串与展示跨日时间；存储一律用 epochMillis。 */
public final class TimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private TimeUtil() {
    }

    /** "yyyy-MM-dd HH:mm"（Asia/Shanghai）→ epochMillis */
    public static long parseScheduled(String text) {
        return LocalDateTime.parse(text, DT).atZone(ZONE).toInstant().toEpochMilli();
    }

    public static String format(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDateTime().format(DT);
    }

    public static String formatShort(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDateTime().format(SHORT);
    }

    /** 跨日天数（按日历日计，同一天为 1） */
    public static int dayCount(long startMillis, long endMillis) {
        LocalDate d1 = Instant.ofEpochMilli(startMillis).atZone(ZONE).toLocalDate();
        LocalDate d2 = Instant.ofEpochMilli(endMillis).atZone(ZONE).toLocalDate();
        return (int) (ChronoUnit.DAYS.between(d1, d2) + 1);
    }

    public static boolean crossDay(long startMillis, long endMillis) {
        return dayCount(startMillis, endMillis) > 1;
    }

    public static long minutesBetween(long a, long b) {
        return Duration.ofMillis(b - a).toMinutes();
    }
}
