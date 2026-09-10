package com.mention.officialAuthentication.util;

import com.mention.officialAuthentication.config.AuthConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 消息发送工具：先替换本地占位符，再（可选）交给 PAPI，最后上色。
 */
public final class Msg {

    private Msg() {
    }

    public static void raw(CommandSender sender, String text, Map<String, String> values) {
        raw(sender, text, values, false);
    }

    public static void raw(CommandSender sender, String text, Map<String, String> values, boolean applyPapi) {
        if (sender == null || text == null || text.isEmpty()) {
            return;
        }
        String out = apply(text, values);
        if (applyPapi && sender instanceof Player) {
            out = PapiBridge.set((Player) sender, out);
        }
        sender.sendMessage(ColorUtil.color(out));
    }

    /**
     * 带配置前缀的管理员消息。
     */
    public static void prefixed(CommandSender sender, AuthConfig config, String text, Map<String, String> values) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String out = apply(text, values);
        if (sender instanceof Player) {
            out = PapiBridge.set((Player) sender, out);
        }
        sender.sendMessage(ColorUtil.color(config.msgPrefix + out));
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
