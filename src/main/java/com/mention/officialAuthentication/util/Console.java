package com.mention.officialAuthentication.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 控制台颜色工具：把 &#RRGGBB / &a / &l 等写法转换成终端能识别的 ANSI 转义码。
 *
 * <p>若终端不支持 ANSI（例如老版 Windows CMD 没有开启 VT），
 * 把 config.yml 的 {@code console.ansi} 设为 false 即可自动去除所有颜色代码。</p>
 */
public final class Console {

    private static final String RESET = "\u001B[0m";
    private static final Pattern HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern CODE = Pattern.compile("(?i)&([0-9a-fk-or])");

    private static volatile boolean ansi = true;

    private Console() {
    }

    public static void setAnsi(boolean enabled) {
        ansi = enabled;
    }

    public static boolean ansiEnabled() {
        return ansi;
    }

    /**
     * 转换成终端可显示的一行文本（自动追加重置码）。
     */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (!ansi) {
            return strip(input);
        }
        String text = HEX.matcher(input).replaceAll(match -> Matcher.quoteReplacement(hexToAnsi(match.group(1))));
        text = CODE.matcher(text).replaceAll(match -> Matcher.quoteReplacement(codeToAnsi(match.group(1).charAt(0))));
        return text + RESET;
    }

    /**
     * 去掉所有颜色/格式代码后的纯文本。
     */
    public static String strip(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = HEX.matcher(input).replaceAll("");
        text = CODE.matcher(text).replaceAll("");
        return text;
    }

    private static String hexToAnsi(String hex) {
        int red = Integer.parseInt(hex.substring(0, 2), 16);
        int green = Integer.parseInt(hex.substring(2, 4), 16);
        int blue = Integer.parseInt(hex.substring(4, 6), 16);
        return "\u001B[38;2;" + red + ";" + green + ";" + blue + "m";
    }

    private static String codeToAnsi(char code) {
        switch (Character.toLowerCase(code)) {
            case '0':
                return "\u001B[30m";
            case '1':
                return "\u001B[34m";
            case '2':
                return "\u001B[32m";
            case '3':
                return "\u001B[36m";
            case '4':
                return "\u001B[31m";
            case '5':
                return "\u001B[35m";
            case '6':
                return "\u001B[33m";
            case '7':
                return "\u001B[37m";
            case '8':
                return "\u001B[90m";
            case '9':
                return "\u001B[94m";
            case 'a':
                return "\u001B[92m";
            case 'b':
                return "\u001B[96m";
            case 'c':
                return "\u001B[91m";
            case 'd':
                return "\u001B[95m";
            case 'e':
                return "\u001B[93m";
            case 'f':
                return "\u001B[97m";
            case 'l':
                return "\u001B[1m";
            case 'm':
                return "\u001B[9m";
            case 'n':
                return "\u001B[4m";
            case 'o':
                return "\u001B[3m";
            case 'r':
                return RESET;
            default:
                return "";
        }
    }
}
