package com.mention.officialAuthentication.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 软依赖桥接：未安装 PAPI 时所有调用都是安全的空操作。
 */
public final class PapiBridge {

    private static final boolean AVAILABLE = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private PapiBridge() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    public static String set(Player player, String text) {
        if (!AVAILABLE || player == null || text == null || text.isEmpty()) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable throwable) {
            return text;
        }
    }
}
