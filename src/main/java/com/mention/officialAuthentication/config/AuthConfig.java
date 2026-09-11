package com.mention.officialAuthentication.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 配置模块。
 *
 * <p>配置被拆成三个文件：</p>
 * <ul>
 *     <li><b>config.yml</b>：开关与连接（本类的绝大部分字段）</li>
 *     <li><b>lang.yml</b>：插件输出的文字（提示语、指令消息、启动横幅）</li>
 *     <li><b>placeholders.yml</b>：占位符显示内容（✔正版 / ✘离线 等）</li>
 * </ul>
 */
public final class AuthConfig {

    /** lang.yml 里 bind-notify.messages 缺失时的内置兜底文案 */
    private static final List<String> DEFAULT_BIND_MESSAGES = Collections.unmodifiableList(Arrays.asList(
            "&#00E5FF&m                                                  &r",
            "&#33DD66&l✔ 正版账号绑定成功",
            "&#8C8C8C▎ &#FFFFFF你已绑定 &#00E5FF%player% &#FFFFFF为正版账号",
            "&#8C8C8C▎ &#FFFFFF正版UUID: &#8C8C8C%real_uuid%",
            "&#8C8C8C▎ &#FFFFFF绑定时间: &#8C8C8C%bind_time%",
            "&#8C8C8C▎ &#FFFFFF绑定状态: %bound% &#8C8C8C| 主服标识: %status%",
            "&#8C8C8C▎ &#8C8C8C以后进入主服会自动显示正版标识, 无需重复认证",
            "&#00E5FF&m                                                  &r"));

    private final JavaPlugin plugin;

    private FileConfiguration mainConf;
    private FileConfiguration langConf;
    private FileConfiguration phConf;

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

    // ---------- MySQL 驱动自动加载 ----------
    public boolean driverAutoDownload = true;
    public String driverVersion = "8.4.0";
    public List<String> driverRepositories = new ArrayList<>(Arrays.asList(
            "https://maven.aliyun.com/repository/public",
            "https://repo1.maven.org/maven2"));
    public int driverTimeoutMs = 15000;

    // ---------- 认证 ----------
    public double expireDays = 30D;
    public long expireMillis = 30L * 24L * 3600L * 1000L;
    public int queryTimeoutSeconds = 5;
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
    public boolean featureAuthExpiry = false;
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

    // ---------- 占位符文案 (placeholders.yml) ----------
    public boolean legacyAsOffline = true;
    public String textPremium = "&#33DD66&l✔正版";
    public String textExpired = "&#FFAA00&l✘已过期";
    public String textFormer = "&#8C8C8C&l✘旧昵称";
    public String textOffline = "&#9E9E9E&l✘离线";
    public String textPending = "&#FFD700&l…查询中";
    public String textBound = "&#33DD66&l✔已绑定";
    public String textUnbound = "&#9E9E9E&l✘未绑定";
    public String boolTrue = "true";
    public String boolFalse = "false";
    public String dateFormat = "yyyy-MM-dd HH:mm:ss";

    // ---------- 消息 (lang.yml) ----------
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
    public String msgUsage = "&c用法: /%label% %sub% <玩家>";
    public List<String> msgHelp = Collections.emptyList();

    // ---------- 认证成功提示 ----------
    public boolean bindNotifyEnabled = true;
    public String bindNotifyMode = "always";
    public String bindTitle = "&#33DD66&l✔正版账号绑定成功";
    public String bindSubtitle = "&#FFFFFF%player% &8· &7主服将显示 %status%";
    public int bindFadeIn = 10;
    public int bindStay = 50;
    public int bindFadeOut = 10;
    public String bindSound = "ENTITY_PLAYER_LEVELUP";
    public float bindSoundVolume = 1.0F;
    public float bindSoundPitch = 1.2F;
    private final Map<String, List<String>> bindMessages = new HashMap<>();

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

    public void reloadFiles() {
        mainConf = plugin.getConfig();
        langConf = loadFile("lang.yml");
        phConf = loadFile("placeholders.yml");
    }

    public void load() {
        reloadFiles();

        // ==================== config.yml ====================
        mode = ServerMode.parse(mainConf.getString("server.mode", "MAIN"));
        debug = mainConf.getBoolean("server.debug", false);
        dbThreads = Math.max(1, mainConf.getInt("server.db-threads", 2));

        dbHost = mainConf.getString("database.host", "127.0.0.1");
        dbPort = String.valueOf(mainConf.get("database.port", "3306"));
        dbName = mainConf.getString("database.database", "minecraft");
        dbUser = mainConf.getString("database.username", "root");
        dbPassword = mainConf.getString("database.password", "");
        tablePrefix = sanitizePrefix(mainConf.getString("database.table-prefix", "officialauth_"));
        poolSize = Math.max(1, mainConf.getInt("database.pool-size", 4));
        connectionTimeoutMs = Math.max(500, mainConf.getInt("database.connection-timeout-ms", 5000));
        extraParams = trim(mainConf.getString("database.extra-params", ""));
        createTablesOnStart = mainConf.getBoolean("database.create-tables-on-start", true);

        driverAutoDownload = mainConf.getBoolean("database.driver.auto-download", true);
        driverVersion = mainConf.getString("database.driver.version", "8.4.0");
        driverTimeoutMs = Math.max(3000, mainConf.getInt("database.driver.timeout-ms", 15000));
        List<String> repositories = new ArrayList<>();
        for (String repository : mainConf.getStringList("database.driver.repositories")) {
            if (repository != null && !repository.trim().isEmpty()) {
                repositories.add(repository.trim());
            }
        }
        if (!repositories.isEmpty()) {
            driverRepositories = Collections.unmodifiableList(repositories);
        }

        expireDays = mainConf.getDouble("auth.expire-days", 30D);
        if (expireDays <= 0D) {
            expireDays = 30D;
        }
        expireMillis = (long) (expireDays * 24D * 3600D * 1000D);
        queryTimeoutSeconds = Math.max(1, mainConf.getInt("auth.query-timeout-seconds", 5));
        cacheSeconds = Math.max(1, mainConf.getInt("auth.cache-seconds", 120));
        cacheOfflineSeconds = Math.max(1, mainConf.getInt("auth.cache-offline-seconds", 20));
        cacheErrorSeconds = Math.max(1, mainConf.getInt("auth.cache-error-seconds", 10));
        cachePurgeMinutes = Math.max(1, mainConf.getInt("auth.cache-purge-minutes", 10));
        maxHistoryNames = Math.max(1, mainConf.getInt("auth.max-history-names", 8));
        allowMainWrite = mainConf.getBoolean("auth.allow-main-write", false);

        cleanupIntervalMinutes = Math.max(1, mainConf.getInt("cleanup.interval-minutes", 60));
        cleanupDeleteExpired = mainConf.getBoolean("cleanup.delete-expired", false);
        historyEnabled = mainConf.getBoolean("cleanup.history-enabled", true);
        historyRetainDays = Math.max(0, mainConf.getInt("cleanup.history-retain-days", 180));

        // ---------- 功能开关 ----------
        featureAuthRecord = mainConf.getBoolean("features.auth-record", true);
        featureNameChain = mainConf.getBoolean("features.name-chain", true);
        featureLegacyInvalidate = mainConf.getBoolean("features.legacy-name-invalidate", true);
        legacyAsOffline = mainConf.getBoolean("features.legacy-name-offline", true);
        featureMainRecognition = mainConf.getBoolean("features.main-recognition", true);
        preLoginLookup = mainConf.getBoolean("features.login-pre-query", true);
        featureCache = mainConf.getBoolean("features.cache", true);
        clearCacheOnQuit = mainConf.getBoolean("features.cache-clear-on-quit", true);
        featureNameHistoryFetch = mainConf.getBoolean("features.name-history-fetch", true);
        featurePlaceholderApi = mainConf.getBoolean("features.placeholder-api", true);
        zbEnabled = mainConf.getBoolean("features.zb-command", true);
        cleanupEnabled = mainConf.getBoolean("features.cleanup", true);

        // 有效期总开关(旧的 features.auth-expiry 仍兼容)
        featureAuthExpiry = mainConf.getBoolean("auth.expiry-enabled",
                mainConf.getBoolean("features.auth-expiry", false));

        fetchNameHistory = featureNameHistoryFetch;
        // 关闭身份链 / 旧昵称失效时无需读取改名历史
        if (!featureNameChain || !featureLegacyInvalidate) {
            fetchNameHistory = false;
            featureNameHistoryFetch = false;
        }

        // ---------- 控制台(开关) ----------
        bannerEnabled = mainConf.getBoolean("console.banner", true);
        bannerAnsi = mainConf.getBoolean("console.ansi", true);

        // ---------- /zb 指令(开关) ----------
        List<String> names = new ArrayList<>();
        for (String name : mainConf.getStringList("command.names")) {
            if (name != null && !name.trim().isEmpty()) {
                names.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        zbNames = names.isEmpty() ? Collections.singletonList("zb") : Collections.unmodifiableList(names);
        zbPermission = mainConf.getString("command.permission", zbPermission);
        zbPermissionRequired = mainConf.getBoolean("command.permission-required", false);
        zbOthersPermission = mainConf.getString("command.others-permission", zbOthersPermission);

        // ---------- 认证成功提示(开关) ----------
        bindNotifyEnabled = mainConf.getBoolean("bind-notify.enabled", true);
        bindNotifyMode = mainConf.getString("bind-notify.mode", "always").trim().toLowerCase(Locale.ROOT);
        bindFadeIn = Math.max(0, mainConf.getInt("bind-notify.fade-in", 10));
        bindStay = Math.max(1, mainConf.getInt("bind-notify.stay", 50));
        bindFadeOut = Math.max(0, mainConf.getInt("bind-notify.fade-out", 10));
        bindSound = mainConf.getString("bind-notify.sound", bindSound);
        bindSoundVolume = (float) mainConf.getDouble("bind-notify.sound-volume", 1.0D);
        bindSoundPitch = (float) mainConf.getDouble("bind-notify.sound-pitch", 1.2D);

        // ==================== placeholders.yml ====================
        textPremium = phConf.getString("status.premium", textPremium);
        textExpired = phConf.getString("status.expired", textExpired);
        textFormer = phConf.getString("status.former", textFormer);
        textOffline = phConf.getString("status.offline", textOffline);
        textPending = phConf.getString("status.pending", textPending);
        textBound = phConf.getString("status.bound", textBound);
        textUnbound = phConf.getString("status.unbound", textUnbound);
        boolTrue = phConf.getString("boolean.true-value", "true");
        boolFalse = phConf.getString("boolean.false-value", "false");
        dateFormat = phConf.getString("date-format", "yyyy-MM-dd HH:mm:ss");

        // ==================== lang.yml ====================
        bannerLines = new ArrayList<>(langConf.getStringList("console.banner-lines"));

        msgPrefix = langConf.getString("messages.prefix", msgPrefix);
        msgNoPermission = langConf.getString("messages.no-permission", msgNoPermission);
        msgPlayerOnly = langConf.getString("messages.player-only", msgPlayerOnly);
        msgReload = langConf.getString("messages.reload", msgReload);
        msgDbError = langConf.getString("messages.db-error", msgDbError);
        msgNotFound = langConf.getString("messages.not-found", msgNotFound);
        msgUnlinkSuccess = langConf.getString("messages.unlink-success", msgUnlinkSuccess);
        msgUnlinkFailed = langConf.getString("messages.unlink-failed", msgUnlinkFailed);
        msgCleanup = langConf.getString("messages.cleanup", msgCleanup);
        msgMainReadOnly = langConf.getString("messages.main-read-only", msgMainReadOnly);
        msgUsage = langConf.getString("messages.usage", msgUsage);
        msgHelp = new ArrayList<>(langConf.getStringList("messages.help"));

        zbDescription = langConf.getString("command.description", zbDescription);
        zbMessages.clear();
        zbMessages.put("default", filterEmpty(langConf.getStringList("command.messages")));
        zbMessages.put("premium", filterEmpty(langConf.getStringList("command.messages-premium")));
        zbMessages.put("expired", filterEmpty(langConf.getStringList("command.messages-expired")));
        zbMessages.put("former", filterEmpty(langConf.getStringList("command.messages-former")));
        zbMessages.put("offline", filterEmpty(langConf.getStringList("command.messages-offline")));

        bindTitle = langConf.getString("bind-notify.title", bindTitle);
        bindSubtitle = langConf.getString("bind-notify.subtitle", bindSubtitle);
        bindMessages.clear();
        bindMessages.put("default", filterEmpty(langConf.getStringList("bind-notify.messages")));
        bindMessages.put("new", filterEmpty(langConf.getStringList("bind-notify.messages-new")));
        bindMessages.put("renamed", filterEmpty(langConf.getStringList("bind-notify.messages-renamed")));
        if (bindMessages.get("default").isEmpty()) {
            bindMessages.put("default", DEFAULT_BIND_MESSAGES);
        }
    }

    /**
     * 读取配置文件，不存在时从 jar 里释放一份默认配置。
     */
    private FileConfiguration loadFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.isFile()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (Throwable ignored) {
                // 资源不存在时忽略, 后续使用内置默认值
            }
        }
        return YamlConfiguration.loadConfiguration(file);
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
     * 功能开关状态（/oauth status 使用）。
     */
    public Map<String, Boolean> featureStates() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("正版认证写库", featureAuthRecord);
        map.put("身份链改名追踪", featureNameChain);
        map.put("认证有效期校验", featureAuthExpiry);
        map.put("旧昵称失效", featureNameChain && featureLegacyInvalidate);
        map.put("旧昵称按离线", legacyAsOffline);
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
     * 是否应该发送绑定成功提示。
     * mode: always = 每次认证都提示 / change = 首次绑定或换绑 / once = 仅首次绑定
     */
    public boolean shouldNotifyBind(boolean firstBind, boolean renamed) {
        if (!bindNotifyEnabled) {
            return false;
        }
        switch (bindNotifyMode) {
            case "once":
                return firstBind;
            case "change":
                return firstBind || renamed;
            default:
                return true;
        }
    }

    /**
     * 取认证成功时应该发送的聊天消息：换绑 > 首次绑定 > 默认。
     */
    public List<String> bindMessagesFor(boolean firstBind, boolean renamed) {
        if (renamed) {
            List<String> renamedLines = bindMessages.get("renamed");
            if (renamedLines != null && !renamedLines.isEmpty()) {
                return renamedLines;
            }
        }
        if (firstBind) {
            List<String> newLines = bindMessages.get("new");
            if (newLines != null && !newLines.isEmpty()) {
                return newLines;
            }
        }
        List<String> defaults = bindMessages.get("default");
        return defaults == null || defaults.isEmpty() ? DEFAULT_BIND_MESSAGES : defaults;
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
