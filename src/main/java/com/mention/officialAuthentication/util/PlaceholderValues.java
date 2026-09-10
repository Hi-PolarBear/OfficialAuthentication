package com.mention.officialAuthentication.util;

import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.config.ServerMode;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.model.AuthStatus;
import com.mention.officialAuthentication.model.NameEntry;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 把 {@link AuthResult} 转换成各种占位符文本，供 PAPI 拓展和 /zb 指令共用。
 *
 * <p>{@code placeholder.legacy-as-offline = true}（默认）时，玩家改名后的旧 ID
 * 会被<b>完全当作离线玩家</b>：状态显示「离线」、布尔为 false、不暴露正版 UUID、
 * 不返回认证时间与昵称链。改成 false 才会显示成「旧昵称」并带出原身份信息。</p>
 */
public final class PlaceholderValues {

    private PlaceholderValues() {
    }

    /** 该结果是否属于「旧昵称」，且当前配置要求按离线处理 */
    public static boolean legacyAsOffline(AuthConfig config, AuthResult result) {
        return config.legacyAsOffline && result != null && result.getStatus() == AuthStatus.FORMER;
    }

    /**
     * 对外是否视为正版。关闭有效期校验时，缓存里残留的「已过期」结果也按正版处理，
     * 避免热重载开关后短时间内显示错乱。
     */
    public static boolean isPremium(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return false;
        }
        if (result.getStatus() == AuthStatus.PREMIUM) {
            return true;
        }
        return !config.featureAuthExpiry && result.getStatus() == AuthStatus.EXPIRED;
    }

    /** 对外展示的状态文本（已应用旧昵称=离线 规则） */
    public static String statusText(AuthConfig config, AuthResult result, boolean loading) {
        if (result == null) {
            return loading ? config.textPending : config.textOffline;
        }
        if (legacyAsOffline(config, result)) {
            return config.textOffline;
        }
        return rawStatusText(config, result);
    }

    /** 真实状态文本（管理员指令用，不受旧昵称=离线 规则影响） */
    public static String rawStatusText(AuthConfig config, AuthResult result) {
        if (result == null) {
            return config.textOffline;
        }
        switch (result.getStatus()) {
            case PREMIUM:
                return config.textPremium;
            case EXPIRED:
                return config.featureAuthExpiry ? config.textExpired : config.textPremium;
            case FORMER:
                return config.textFormer;
            default:
                return config.textOffline;
        }
    }

    /** /zb 指令使用的状态键：premium / expired / former / offline */
    public static String stateKey(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return "offline";
        }
        switch (result.getStatus()) {
            case PREMIUM:
                return "premium";
            case EXPIRED:
                return config.featureAuthExpiry ? "expired" : "premium";
            case FORMER:
                return "former";
            default:
                return "offline";
        }
    }

    public static String boolText(AuthConfig config, AuthResult result) {
        return isPremium(config, result) ? config.boolTrue : config.boolFalse;
    }

    public static String knownText(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return config.boolFalse;
        }
        if (!config.featureAuthExpiry && result.getStatus() == AuthStatus.EXPIRED) {
            return config.boolTrue;
        }
        return result.isKnown() ? config.boolTrue : config.boolFalse;
    }

    public static String realUuid(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result) || result.getRealUuid() == null) {
            return "";
        }
        return result.getRealUuid();
    }

    public static String offlineUuid(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 正版玩家返回正版 UUID，其余一律返回离线 UUID */
    public static String uuid(AuthConfig config, AuthResult result, String playerName) {
        String real = realUuid(config, result);
        return real.isEmpty() ? offlineUuid(playerName) : real;
    }

    public static String currentName(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result) || result.getCurrentName() == null) {
            return "-";
        }
        return result.getCurrentName();
    }

    public static String lastAuth(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return "-";
        }
        return TimeUtil.format(result.getLastAuth(), config.dateFormat);
    }

    public static String lastAuthRaw(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return "0";
        }
        return String.valueOf(result.getLastAuth());
    }

    public static String firstAuth(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return "-";
        }
        return TimeUtil.format(result.getFirstAuth(), config.dateFormat);
    }

    public static String expireIn(AuthConfig config, AuthResult result) {
        if (!config.featureAuthExpiry) {
            return "永久";
        }
        if (result == null || legacyAsOffline(config, result) || result.getExpireAt() <= 0L || !result.isKnown()) {
            return "-";
        }
        long remaining = result.getRemainingMillis();
        return remaining <= 0L ? "已过期" : TimeUtil.humanize(remaining);
    }

    public static String expireDays(AuthConfig config, AuthResult result) {
        if (!config.featureAuthExpiry) {
            return "-";
        }
        if (result == null || legacyAsOffline(config, result) || !result.isKnown() || result.getExpireAt() <= 0L) {
            return "0";
        }
        long remaining = result.getRemainingMillis();
        return String.valueOf(remaining / (24L * 3600L * 1000L));
    }

    /** 昵称链：当前昵称在最前，旧昵称用括号标记；旧昵称=离线 时返回 - */
    public static String names(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result) || result.getNames().isEmpty()) {
            return "-";
        }
        List<NameEntry> entries = result.getNames();
        StringJoiner joiner = new StringJoiner("&7, ");
        for (NameEntry entry : entries) {
            joiner.add(entry.isCurrent() ? entry.getName() : entry.getName() + "&8(旧)");
        }
        return joiner.toString();
    }

    public static String namesCount(AuthConfig config, AuthResult result) {
        if (result == null || legacyAsOffline(config, result)) {
            return "0";
        }
        return String.valueOf(result.getNames().size());
    }

    /**
     * 生成 /zb 指令可用的占位符表。
     */
    public static Map<String, String> build(AuthConfig config, ServerMode mode, boolean serverOnlineMode,
                                            String playerName, AuthResult result, boolean loading) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", playerName == null ? "-" : playerName);
        values.put("status", statusText(config, result, loading));
        values.put("bool", boolText(config, result));
        values.put("known", knownText(config, result));
        String real = realUuid(config, result);
        values.put("real_uuid", real.isEmpty() ? "-" : real);
        values.put("uuid", uuid(config, result, playerName));
        values.put("offline_uuid", offlineUuid(playerName));
        values.put("current_name", currentName(config, result));
        values.put("last_auth", lastAuth(config, result));
        values.put("last_auth_raw", lastAuthRaw(config, result));
        values.put("first_auth", firstAuth(config, result));
        values.put("expire_in", expireIn(config, result));
        values.put("expire_days", expireDays(config, result));
        values.put("names", names(config, result));
        values.put("names_count", namesCount(config, result));
        values.put("mode", mode == null ? "MAIN" : mode.name());
        values.put("online_mode", String.valueOf(serverOnlineMode));
        values.put("error", result == null || result.getError() == null ? "-" : result.getError());
        return values;
    }
}
