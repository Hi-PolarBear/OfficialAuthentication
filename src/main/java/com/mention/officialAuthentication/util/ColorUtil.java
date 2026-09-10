package com.mention.officialAuthentication.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色工具：支持 &#RRGGBB 十六进制颜色 + 传统 &a/&l 颜色代码。
 */
public final class ColorUtil {

    /** 形如 &#00E5FF */
    private static final Pattern HEX_AMP = Pattern.compile("(?i)&#([0-9a-f]{6})");
    /** 形如 #00E5FF */
    private static final Pattern HEX_HASH = Pattern.compile("(?i)(?<!&)#([0-9a-f]{6})");

    private ColorUtil() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = HEX_AMP.matcher(input).replaceAll(match -> toSection(match.group(1)));
        text = HEX_HASH.matcher(text).replaceAll(match -> toSection(match.group(1)));
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }

    /**
     * #RRGGBB -> §x§R§R§G§G§B§B
     */
    private static String toSection(String hex) {
        StringBuilder builder = new StringBuilder(14).append('\u00a7').append('x');
        for (int i = 0; i < hex.length(); i++) {
            builder.append('\u00a7').append(Character.toLowerCase(hex.charAt(i)));
        }
        return Matcher.quoteReplacement(builder.toString());
    }
}
