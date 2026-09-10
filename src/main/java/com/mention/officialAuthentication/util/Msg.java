package com.mention.officialAuthentication.util;

import com.mention.officialAuthentication.config.AuthConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 消息发送工具：先交给 PlaceholderAPI（未安装则跳过），再替换本地占位符，最后上色。
 */
public final class Msg {

    private Msg() {
    }

    /**
     * 格式化文本（不发送）：PAPI -> 本地占位符 -> 颜色代码。
     *
     * @param viewer 若不为 null 则解析该玩家可见的 PAPI 占位符
     */
    public static String format(Player viewer, String text, Map<String, String> values) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String out = text;
        if (viewer != null) {
            out = PapiBridge.set(viewer, out);
        }
        out = apply(out, values);
        return ColorUtil.color(out);
    }

    public static void raw(CommandSender sender, String text, Map<String, String> values) {
        raw(sender, text, values, false);
    }

    public static void raw(CommandSender sender, String text, Map<String, String> values, boolean applyPapi) {
        if (sender == null || text == null || text.isEmpty()) {
            return;
        }
        Player viewer = applyPapi && sender instanceof Player ? (Player) sender : null;
        sender.sendMessage(format(viewer, text, values));
    }

    /**
     * 带配置前缀的管理员消息。
     */
    public static void prefixed(CommandSender sender, AuthConfig config, String text, Map<String, String> values) {
        if (sender == null || text == null || text.isEmpty()) {
            return;
        }
        Player viewer = sender instanceof Player ? (Player) sender : null;
        sender.sendMessage(format(viewer, config.msgPrefix + text, values));
    }

    public static void simple(CommandSender sender, AuthConfig config, String text) {
        prefixed(sender, config, text, null);
    }

    private static String apply(String text, Map<String, String> values) {
        String out = text;
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String placeholder = "%" + entry.getKey() + "%";
                if (out.contains(placeholder)) {
                    out = out.replace(placeholder, entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        return out;
    }
}
