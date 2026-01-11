package com.echocampus.bot.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateTimeUtil {

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    public static LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }
}
