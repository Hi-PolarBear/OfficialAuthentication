package com.mention.officialAuthentication.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String format(long millis, String pattern) {
        if (millis <= 0L) {
            return "-";
        }
        return new SimpleDateFormat(pattern).format(new Date(millis));
    }

    /**
     * 把毫秒转换成「1天2小时」这样的可读文本。
     */
    public static String humanize(long millis) {
        if (millis <= 0L) {
            return "已过期";
        }
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append("天");
        }
        if (hours > 0L) {
            builder.append(hours).append("小时");
        }
        if (days == 0L && minutes > 0L) {
            builder.append(minutes).append("分钟");
        }
        if (builder.length() == 0) {
            builder.append("不足1分钟");
        }
        return builder.toString();
    }
}
