package com.mention.officialAuthentication.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * 播放音效（按名称，兼容原版键名与旧版枚举名）。
 */
public final class SoundPlayer {

    private SoundPlayer() {
    }

    /**
     * @param sound  音效名，支持 {@code entity.player.levelup} 与 {@code ENTITY_PLAYER_LEVELUP} 两种写法，留空不播放
     * @param volume 音量
     * @param pitch  音调
     */
    public static void play(Player player, String sound, float volume, float pitch, Logger logger) {
        if (player == null || sound == null || sound.trim().isEmpty()) {
            return;
        }
        String key = normalize(sound);
        try {
            Location location = player.getLocation();
            player.playSound(location, key, volume, pitch);
        } catch (Throwable throwable) {
            if (logger != null) {
                logger.warning("播放音效失败, 请检查 bind-notify.sound 是否为有效音效名: " + sound);
            }
        }
    }

    /**
     * ENTITY_PLAYER_LEVELUP -> entity.player.levelup
     */
    private static String normalize(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.indexOf('.') >= 0 || value.indexOf(':') >= 0) {
            return value;
        }
        return value.replace('_', '.');
    }
}
