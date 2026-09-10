package com.mention.officialAuthentication.listener;

import com.mention.officialAuthentication.OfficialAuthentication;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.db.AuthRepository;
import com.mention.officialAuthentication.model.AuthResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 登录监听：
 * <ul>
 *     <li>认证服 (AUTH)：玩家成功进入后异步覆盖写库</li>
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
     * 认证服：抓取正版信息并异步写库。主服：确保缓存里有数据。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMode().isAuth()) {
            repository.recordAuthentication(player.getUniqueId(), player.getName());
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
}
