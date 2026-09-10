package com.mention.officialAuthentication.listener;

import com.mention.officialAuthentication.OfficialAuthentication;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.db.AuthRepository;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.model.AuthStatus;
import com.mention.officialAuthentication.model.BindResult;
import com.mention.officialAuthentication.util.Msg;
import com.mention.officialAuthentication.util.PlaceholderValues;
import com.mention.officialAuthentication.util.SoundPlayer;
import com.mention.officialAuthentication.util.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录监听：
 * <ul>
 *     <li>认证服 (AUTH)：玩家成功进入后异步覆盖写库，并发送「绑定成功」标题与聊天提示</li>
 *     <li>主服 (MAIN)：登录前异步预查询 + 进服缓存 + 退服清缓存</li>
 * </ul>
 */
public final class PlayerListener implements Listener {

    private final OfficialAuthentication plugin;
    private final AuthConfig config;
    private final AuthService service;
    private final AuthRepository repository;

    public PlayerListener(OfficialAuthentication plugin, AuthConfig config,
                          AuthService service, AuthRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.repository = repository;
    }

    /**
     * 主服：登录前（本身就是异步线程）先查一次库，玩家进服瞬间占位符就有数据。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getMode().isMain() || !config.preLoginLookup) {
            return;
        }
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        String name = event.getName();
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            AuthResult result = service.resolveBlocking(name, service.queryTimeoutMillis());
            if (config.debug) {
                plugin.getLogger().info("[调试] 预查询 " + name + " -> " + result);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("预查询玩家 " + name + " 认证信息时异常: " + throwable.getMessage());
        }
    }

    /**
     * 认证服：抓取正版信息并异步写库（成功后发送绑定提示）。主服：确保缓存里有数据。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMode().isAuth()) {
            recordAndNotify(player);
            return;
        }
        service.resolveAsync(player.getName());
    }

    /**
     * 退服清空缓存。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (config.clearCacheOnQuit) {
            service.invalidate(event.getPlayer().getName());
        }
    }

    // =========================================================
    //  正版认证服：绑定成功提示
    // =========================================================

    private void recordAndNotify(Player player) {
        final String playerName = player.getName();
        repository.recordAuthentication(player.getUniqueId(), playerName).whenComplete((result, error) -> {
            if (result == null || !result.isSuccess()) {
                if (config.debug && result != null) {
                    plugin.getLogger().info("[调试] " + playerName + " 认证未写入: " + result.getError());
                }
                return;
            }
            AuthConfig current = plugin.getAuthConfig();
            if (!current.shouldNotifyBind(result.isFirstBind(), result.isRenamed())) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> sendBindNotify(player, result));
        });
    }

    @SuppressWarnings("deprecation")
    private void sendBindNotify(Player player, BindResult result) {
        if (!player.isOnline()) {
            return;
        }
        AuthConfig current = plugin.getAuthConfig();

        long expireAt = current.featureAuthExpiry ? result.getTime() + current.expireMillis : 0L;
        AuthResult auth = new AuthResult(result.getName(), AuthStatus.PREMIUM, result.getRealUuid(), result.getName(),
                result.getTime(), result.getTime(), expireAt, Collections.emptyList(), null);

        Map<String, String> values = new LinkedHashMap<>(PlaceholderValues.build(current, plugin.getMode(),
                plugin.getServer().getOnlineMode(), result.getName(), auth, false));
        values.put("bind_time", TimeUtil.format(result.getTime(), current.dateFormat));
        values.put("previous_name", result.getPreviousName() == null ? "-" : result.getPreviousName());
        values.put("first_bind", result.isFirstBind() ? current.boolTrue : current.boolFalse);
        values.put("renamed", result.isRenamed() ? current.boolTrue : current.boolFalse);
        values.put("bind_state", result.isFirstBind() ? "首次绑定" : (result.isRenamed() ? "换绑" : "刷新认证"));

        // 屏幕中央标题（横屏大字）
        String title = Msg.format(player, current.bindTitle, values);
        String subtitle = Msg.format(player, current.bindSubtitle, values);
        if (!title.isEmpty() || !subtitle.isEmpty()) {
            player.sendTitle(title, subtitle, current.bindFadeIn, current.bindStay, current.bindFadeOut);
        }

        // 聊天栏提示
        for (String line : current.bindMessagesFor(result.isFirstBind(), result.isRenamed())) {
            Msg.raw(player, line, values, true);
        }

        // 音效（可选）
        SoundPlayer.play(player, current.bindSound, current.bindSoundVolume, current.bindSoundPitch, plugin.getLogger());
    }
}
