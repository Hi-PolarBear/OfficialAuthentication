package com.mention.officialAuthentication.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 配置模块：读取并缓存 config.yml 的所有设置。
 */
public final class AuthConfig {

    private final JavaPlugin plugin;

    // ---------- 服务端 ----------
    public ServerMode mode = ServerMode.MAIN;
    public boolean debug = false;
    public int dbThreads = 2;

    // ---------- 数据库 ----------
    public String dbHost = "127.0.0.1";
    public String dbPort = "3306";
    public String dbName = "minecraft";
    public String dbUser = "root";
    public String dbPassword = "";
    public String tablePrefix = "officialauth_";
    public int poolSize = 4;
    public int connectionTimeoutMs = 5000;
    public String extraParams = "";
    public boolean createTablesOnStart = true;

    // ---------- 认证 ----------
    public double expireDays = 30D;
    public long expireMillis = 30L * 24L * 3600L * 1000L;    public int queryTimeoutSeconds = 5;
    public int cacheSeconds = 120;
    public int cacheOfflineSeconds = 20;
    public int cacheErrorSeconds = 10;
    public int cachePurgeMinutes = 10;
    public boolean clearCacheOnQuit = true;
    public boolean preLoginLookup = true;
    public boolean fetchNameHistory = true;
    public int maxHistoryNames = 8;
    public boolean allowMainWrite = false;

    // ---------- 功能开关 ----------
    public boolean featureAuthRecord = true;
    public boolean featureNameChain = true;
    public boolean featureAuthExpiry = true;
    public boolean featureLegacyInvalidate = true;
    public boolean featureMainRecognition = true;
    public boolean featureCache = true;
    public boolean featureNameHistoryFetch = true;
    public boolean featurePlaceholderApi = true;

    // ---------- 控制台 ----------
    public boolean bannerEnabled = true;
    public boolean bannerAnsi = true;
    public List<String> bannerLines = Collections.emptyList();

    // ---------- 清理 ----------
    public boolean cleanupEnabled = true;
    public int cleanupIntervalMinutes = 60;
    public boolean cleanupDeleteExpired = false;
    public boolean historyEnabled = true;
    public int historyRetainDays = 180;

    // ---------- 文案 ----------
    public boolean legacyAsOffline = true;
    public String textPremium = "&#33DD66&l✔正版";
    public String textExpired = "&#FFAA00&l✘已过期";
    public String textFormer = "&#8C8C8C&l✘旧昵称";
    public String textOffline = "&#9E9E9E&l✘离线";
    public String textPending = "&#FFD700&l…查询中";
    public String boolTrue = "true";
    public String boolFalse = "false";
    public String dateFormat = "yyyy-MM-dd HH:mm:ss";

    // ---------- 消息 ----------
    public String msgPrefix = "&#00E5FF&l正版认证 &8» &r";
    public String msgNoPermission = "&#FF5555你没有权限执行该指令。";
    public String msgPlayerOnly = "&#FF5555该指令只能由玩家执行。";
    public String msgReload = "&#00E5FF配置已重载";
    public String msgDbError = "&#FF5555数据库操作失败: &#FFFFFF%error%";
    public String msgNotFound = "&#FF5555找不到玩家 &#FFFFFF%player% &#FF5555的认证记录。";
    public String msgUnlinkSuccess = "&#00E5FF已解除正版绑定。";
    public String msgUnlinkFailed = "&#FF5555解除失败。";
    public String msgCleanup = "&#00E5FF清理完成, 受影响记录: &#FFFFFF%count% &#00E5FF条。";
    public String msgMainReadOnly = "&#FF5555当前是主服模式(只读), 禁止写入。";
    public List<String> msgHelp = Collections.emptyList();

    // ---------- /zb 指令 ----------
    public boolean zbEnabled = true;
    public List<String> zbNames = Collections.singletonList("zb");
    public String zbDescription = "查看正版认证状态";
    public String zbPermission = "officialauth.zb";
    public boolean zbPermissionRequired = false;
    public String zbOthersPermission = "officialauth.zb.others";
    private final Map<String, List<String>> zbMessages = new HashMap<>();

    public AuthConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();

        mode = ServerMode.parse(c.getString("server.mode", "MAIN"));
        debug = c.getBoolean("server.debug", false);
        dbThreads = Math.max(1, c.getInt("server.db-threads", 2));

        dbHost = c.getString("database.host", "127.0.0.1");
        dbPort = String.valueOf(c.get("database.port", "3306"));
        dbName = c.getString("database.database", "minecraft");
        dbUser = c.getString("database.username", "root");
        dbPassword = c.getString("database.password", "");
        tablePrefix = sanitizePrefix(c.getString("database.table-prefix", "officialauth_"));
        poolSize = Math.max(1, c.getInt("database.pool-size", 4));
        connectionTimeoutMs = Math.max(500, c.getInt("database.connection-timeout-ms", 5000));
        extraParams = trim(c.getString("database.extra-params", ""));
        createTablesOnStart = c.getBoolean("database.create-tables-on-start", true);

        expireDays = c.getDouble("auth.expire-days", 30D);
        if (expireDays <= 0D) {
            expireDays = 30D;
        }
        expireMillis = (long) (expireDays * 24D * 3600D * 1000D);
        queryTimeoutSeconds = Math.max(1, c.getInt("auth.query-timeout-seconds", 5));
        cacheSeconds = Math.max(1, c.getInt("auth.cache-seconds", 120));
        cacheOfflineSeconds = Math.max(1, c.getInt("auth.cache-offline-seconds", 20));
        cacheErrorSeconds = Math.max(1, c.getInt("auth.cache-error-seconds", 10));
        cachePurgeMinutes = Math.max(1, c.getInt("auth.cache-purge-minutes", 10));
        clearCacheOnQuit = c.getBoolean("features.cache-clear-on-quit", c.getBoolean("auth.clear-cache-on-quit", true));
        preLoginLookup = c.getBoolean("features.login-pre-query", c.getBoolean("auth.pre-login-lookup", true));
        fetchNameHistory = c.getBoolean("features.name-history-fetch", c.getBoolean("auth.fetch-name-history", true));
        maxHistoryNames = Math.max(1, c.getInt("auth.max-history-names", 8));
        allowMainWrite = c.getBoolean("auth.allow-main-write", false);

        featureAuthRecord = c.getBoolean("features.auth-record", true);
        featureNameChain = c.getBoolean("features.name-chain", true);
        featureAuthExpiry = c.getBoolean("auth.expiry-enabled", c.getBoolean("features.auth-expiry", false));
        featureLegacyInvalidate = c.getBoolean("features.legacy-name-invalidate", true);
        featureMainRecognition = c.getBoolean("features.main-recognition", true);
        featureCache = c.getBoolean("features.cache", true);
        featureNameHistoryFetch = c.getBoolean("features.name-history-fetch", fetchNameHistory);
        featurePlaceholderApi = c.getBoolean("features.placeholder-api", true);

        bannerEnabled = c.getBoolean("console.banner", true);
        bannerAnsi = c.getBoolean("console.ansi", true);
        bannerLines = new ArrayList<>(c.getStringList("console.banner-lines"));

        // 关掉身份链 / 旧昵称失效时无需读取改名历史
        if (!featureNameChain || !featureLegacyInvalidate) {
            fetchNameHistory = false;
            featureNameHistoryFetch = false;
        }

        cleanupEnabled = c.getBoolean("features.cleanup", c.getBoolean("cleanup.enabled", true));
        cleanupIntervalMinutes = Math.max(1, c.getInt("cleanup.interval-minutes", 60));
        cleanupDeleteExpired = c.getBoolean("cleanup.delete-expired", false);
        historyEnabled = c.getBoolean("cleanup.history-enabled", true);
        historyRetainDays = Math.max(0, c.getInt("cleanup.history-retain-days", 180));

        legacyAsOffline = c.getBoolean("placeholder.legacy-as-offline", true);
        textPremium = c.getString("placeholder.premium", textPremium);
        textExpired = c.getString("placeholder.expired", textExpired);
        textFormer = c.getString("placeholder.former", textFormer);
        textOffline = c.getString("placeholder.offline", textOffline);
        textPending = c.getString("placeholder.pending", textPending);
        boolTrue = c.getString("placeholder.true-value", "true");
        boolFalse = c.getString("placeholder.false-value", "false");
        dateFormat = c.getString("placeholder.date-format", "yyyy-MM-dd HH:mm:ss");

        msgPrefix = c.getString("messages.prefix", msgPrefix);
        msgNoPermission = c.getString("messages.no-permission", msgNoPermission);
        msgPlayerOnly = c.getString("messages.player-only", msgPlayerOnly);
        msgReload = c.getString("messages.reload", msgReload);
        msgDbError = c.getString("messages.db-error", msgDbError);
        msgNotFound = c.getString("messages.not-found", msgNotFound);
        msgUnlinkSuccess = c.getString("messages.unlink-success", msgUnlinkSuccess);
        msgUnlinkFailed = c.getString("messages.unlink-failed", msgUnlinkFailed);
        msgCleanup = c.getString("messages.cleanup", msgCleanup);
        msgMainReadOnly = c.getString("messages.main-read-only", msgMainReadOnly);
        msgHelp = new ArrayList<>(c.getStringList("messages.help"));

        zbEnabled = c.getBoolean("features.zb-command", c.getBoolean("command.enabled", true));
        List<String> names = new ArrayList<>();
        for (String name : c.getStringList("command.names")) {
            if (name != null && !name.trim().isEmpty()) {
                names.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        zbNames = names.isEmpty() ? Collections.singletonList("zb") : Collections.unmodifiableList(names);
        zbDescription = c.getString("command.description", zbDescription);
        zbPermission = c.getString("command.permission", zbPermission);
        zbPermissionRequired = c.getBoolean("command.permission-required", false);
        zbOthersPermission = c.getString("command.others-permission", zbOthersPermission);

        zbMessages.clear();
        zbMessages.put("default", filterEmpty(c.getStringList("command.messages")));
        zbMessages.put("premium", filterEmpty(c.getStringList("command.messages-premium")));
        zbMessages.put("expired", filterEmpty(c.getStringList("command.messages-expired")));
        zbMessages.put("former", filterEmpty(c.getStringList("command.messages-former")));
        zbMessages.put("offline", filterEmpty(c.getStringList("command.messages-offline")));
    }

    /**
     * 有效期文本，如 "30 天" / "永久有效(不过期)"。
     */
    public String expireText() {
        if (!featureAuthExpiry) {
            return "永久有效(不过期)";
        }
        String days = expireDays == Math.floor(expireDays)
                ? String.valueOf((long) expireDays)
                : String.valueOf(expireDays);
        return days + " 天";
    }

    /**
     * 功能开关状态（控制台横幅与 /oauth status 共用）。
     */
    public Map<String, Boolean> featureStates() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("正版认证写库", featureAuthRecord);
        map.put("身份链改名追踪", featureNameChain);
        map.put("认证有效期校验", featureAuthExpiry);
        map.put("旧昵称失效", featureNameChain && featureLegacyInvalidate);
        map.put("主服识别查询", featureMainRecognition);
        map.put("登录预查询", featureMainRecognition && preLoginLookup);
        map.put("结果缓存", featureCache);
        map.put("退服清缓存", featureCache && clearCacheOnQuit);
        map.put("改名历史查询", featureNameHistoryFetch);
        map.put("PAPI占位符", featurePlaceholderApi);
        map.put("/zb 指令", zbEnabled);
        map.put("定时清理", cleanupEnabled);
        return map;
    }

    /** 已开启的功能列表，如：&f认证写库&8, &f结果缓存 */
    public String enabledFeatures() {
        StringJoiner joiner = new StringJoiner("&8, &f");
        for (Map.Entry<String, Boolean> entry : featureStates().entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                joiner.add(entry.getKey());
            }
        }
        return joiner.length() == 0 ? "&8无" : joiner.toString();
    }

    /** 已关闭的功能列表，全部开启时返回绿色「无」 */
    public String disabledFeatures() {
        StringJoiner joiner = new StringJoiner("&8, &e");
        for (Map.Entry<String, Boolean> entry : featureStates().entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                joiner.add(entry.getKey());
            }
        }
        return joiner.length() == 0 ? "&a无" : joiner.toString();
    }

    /**
     * 取某个状态对应的 /zb 消息；没有单独配置则回落到默认 messages。
     */
    public List<String> zbMessagesFor(String state) {
        if (state != null) {
            List<String> lines = zbMessages.get(state);
            if (lines != null && !lines.isEmpty()) {
                return lines;
            }
        }
        List<String> defaults = zbMessages.get("default");
        return defaults == null ? Collections.emptyList() : defaults;
    }

    public boolean zbRegisteredName(String name) {
        return name != null && zbNames.contains(name.toLowerCase(Locale.ROOT));
    }

    private static List<String> filterEmpty(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input != null) {
            for (String line : input) {
                if (line != null && !line.trim().isEmpty()) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    private static String sanitizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : prefix.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
