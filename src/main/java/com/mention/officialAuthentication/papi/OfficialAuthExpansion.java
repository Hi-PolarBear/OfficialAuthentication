package com.mention.officialAuthentication.papi;

import com.mention.officialAuthentication.OfficialAuthentication;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.util.PlaceholderValues;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * PAPI 拓展：注册 %officialauth_xxx% 占位符。
 *
 * <pre>
 * %officialauth_status%         正版 / 已过期 / 旧昵称 / 离线 (带颜色, 可配置)
 * %officialauth_bool%           true / false
 * %officialauth_ispremium%      同上
 * %officialauth_known%          数据库里是否存在该昵称的记录
 * %officialauth_real_uuid%      正版原生 UUID (离线玩家为空)
 * %officialauth_uuid%           正版 UUID, 离线玩家返回离线 UUID
 * %officialauth_offline_uuid%   该昵称的离线 UUID
 * %officialauth_current_name%   该正版身份当前正在使用的昵称
 * %officialauth_last_auth%      最后认证时间
 * %officialauth_last_auth_raw%  最后认证时间戳
 * %officialauth_first_auth%     首次认证时间
 * %officialauth_expire_in%      距离过期剩余时间
 * %officialauth_expire_days%    距离过期剩余天数
 * %officialauth_names%          该身份的全部昵称(当前在前)
 * %officialauth_names_count%    昵称数量
 * %officialauth_mode%           当前服务端角色 AUTH / MAIN
 * </pre>
 */
public final class OfficialAuthExpansion extends PlaceholderExpansion {

    private final OfficialAuthentication plugin;
    private final AuthConfig config;
    private final AuthService service;

    public OfficialAuthExpansion(OfficialAuthentication plugin, AuthConfig config, AuthService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @Override
    public String getIdentifier() {
        return "officialauth";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        String name = player.getName();
        if (name == null || name.isEmpty()) {
            return "";
        }
        String param = params.toLowerCase(Locale.ROOT).trim();

        AuthResult result = service.getCached(name);
        boolean loading = false;
        if (result == null) {
            service.resolveAsync(name);
            loading = true;
        }

        switch (param) {
            case "status":
            case "state":
            case "text":
                return PlaceholderValues.statusText(config, result, loading);
            case "bool":
            case "ispremium":
            case "premium":
                return PlaceholderValues.boolText(config, result);
            case "known":
                return loading && result == null ? config.boolFalse : PlaceholderValues.knownText(config, result);
            case "real_uuid":
            case "realuuid":
                return PlaceholderValues.realUuid(config, result);
            case "uuid":
                return PlaceholderValues.uuid(config, result, name);
            case "offline_uuid":
                return PlaceholderValues.offlineUuid(name);
            case "current_name":
            case "identity_name":
                return PlaceholderValues.currentName(config, result);
            case "last_auth":
                return PlaceholderValues.lastAuth(config, result);
            case "last_auth_raw":
                return PlaceholderValues.lastAuthRaw(config, result);
            case "first_auth":
                return PlaceholderValues.firstAuth(config, result);
            case "expire_in":
                return PlaceholderValues.expireIn(config, result);
            case "expire_days":
                return PlaceholderValues.expireDays(config, result);
            case "names":
            case "name_history":
                return PlaceholderValues.names(config, result);
            case "names_count":
                return PlaceholderValues.namesCount(config, result);
            case "mode":
                return plugin.getMode().name();
            case "server_online_mode":
                return String.valueOf(plugin.getServer().getOnlineMode());
            default:
                return "";
        }
    }
}
