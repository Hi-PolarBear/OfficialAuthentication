package com.mention.officialAuthentication;

import com.mention.officialAuthentication.cache.AuthCache;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.command.OfficialAuthCommand;
import com.mention.officialAuthentication.command.ZbCommand;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.config.ServerMode;
import com.mention.officialAuthentication.db.AuthRepository;
import com.mention.officialAuthentication.db.DatabaseManager;
import com.mention.officialAuthentication.listener.PlayerListener;
import com.mention.officialAuthentication.papi.OfficialAuthExpansion;
import com.mention.officialAuthentication.util.Console;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * 主类：单插件双模式（正版认证服 / 主服）。
 */
public final class OfficialAuthentication extends JavaPlugin {

    private final List<Command> registeredZbCommands = new ArrayList<>();

    private AuthConfig authConfig;
    private ServerMode mode = ServerMode.MAIN;
    private DatabaseManager database;
    private AuthRepository repository;
    private AuthCache cache;
    private AuthService authService;
    private ExecutorService dbExecutor;
    private OfficialAuthExpansion expansion;
    private BukkitTask cacheTask;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        authConfig = new AuthConfig(this);
        try {
            authConfig.load();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "读取 config.yml 失败", ex);
        }
        mode = authConfig.mode;
        Console.setAnsi(authConfig.bannerAnsi);

        dbExecutor = Executors.newFixedThreadPool(authConfig.dbThreads, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "OfficialAuth-DB-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });

        database = new DatabaseManager(this, authConfig);
        try {
            database.init();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "数据库初始化失败: " + ex.getMessage(), ex);
            getLogger().severe("插件仍会继续加载, 请修正 database 配置后执行 /officialauth reload");
        }

        repository = new AuthRepository(this, authConfig, database, dbExecutor);
        cache = new AuthCache(authConfig);
        authService = new AuthService(authConfig, repository, cache);

        getServer().getPluginManager().registerEvents(new PlayerListener(this, authConfig, authService, repository), this);

        registerAdminCommand();
        registerZbCommands();
        registerPlaceholderApi();
        scheduleTasks();
        checkMode();
        printBanner();
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // ignore
            }
            expansion = null;
        }
        unregisterZbCommands();
        if (database != null) {
            database.close();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
        }
        getLogger().info(Console.strip("&#00E5FF[正版认证] 插件已卸载"));
    }

    // =========================================================
    //  控制台启动打印
    // =========================================================

    private void printBanner() {
        if (!authConfig.bannerEnabled || authConfig.bannerLines.isEmpty()) {
            info("&#00E5FF[正版认证] &#FFFFFFOfficialAuthentication v" + getDescription().getVersion()
                    + " &#8C8C8C已启用 | 模式: &f" + mode
                    + " &#8C8C8C| online-mode: &f" + getServer().getOnlineMode());
            return;
        }
        Map<String, String> values = bannerValues();
        for (String raw : authConfig.bannerLines) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String line = raw;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            getLogger().info(Console.color(line));
        }
        if (!Console.ansiEnabled()) {
            getLogger().info("(console.ansi = false, 已去除颜色代码)");
        }
    }

    private Map<String, String> bannerValues() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("version", getDescription().getVersion());
        map.put("server", getServer().getName() + " " + getServer().getBukkitVersion());
        map.put("mode", mode.name());
        map.put("mode_desc", mode.isAuth() ? "正版认证服 · 唯一写库" : "主服 · 只读识别");
        map.put("online_mode", String.valueOf(getServer().getOnlineMode()));
        map.put("database", database.isReady() ? "&a已连接" : "&c未就绪");
        map.put("database_addr", authConfig.dbHost + ":" + authConfig.dbPort + "/" + authConfig.dbName);
        map.put("expire", "&a" + authConfig.expireText());
        map.put("papi", getServer().getPluginManager().getPlugin("PlaceholderAPI") != null ? "&a已加载" : "&e未安装");
        map.put("zb", authConfig.zbEnabled ? "&a/" + authConfig.zbNames.get(0) : "&c已关闭");
        map.put("features", authConfig.enabledFeatures());
        map.put("disabled", authConfig.disabledFeatures());
        map.put("prefix", authConfig.tablePrefix);
        map.put("threads", String.valueOf(authConfig.dbThreads));
        return map;
    }

    private void info(String text) {
        getLogger().info(Console.color(text));
    }

    // =========================================================
    //  注册
    // =========================================================

    private void registerAdminCommand() {
        PluginCommand pluginCommand = getCommand("officialauth");
        if (pluginCommand == null) {
            getLogger().warning("指令 /officialauth 未在 plugin.yml 中声明");
            return;
        }
        OfficialAuthCommand executor = new OfficialAuthCommand(this, authService, repository);
        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(executor);
    }

    /**
     * 按配置注册 /zb 指令（名称、别名都可以在 config.yml 里改）。
     */
    private void registerZbCommands() {
        unregisterZbCommands();
        if (!authConfig.zbEnabled) {
            info("&#FFB300[正版认证] features.zb-command = false, 已跳过 /zb 指令注册");
            return;
        }
        List<String> names = new ArrayList<>(authConfig.zbNames);
        if (names.isEmpty()) {
            return;
        }
        String main = names.remove(0);
        List<String> aliases = names.isEmpty() ? new ArrayList<>() : names;
        ZbCommand command = new ZbCommand(main, aliases, this, authConfig, authService);
        CommandMap commandMap = getServer().getCommandMap();
        try {
            commandMap.register(getName().toLowerCase(Locale.ROOT), command);
            registeredZbCommands.add(command);
            info("&#00E5FF[正版认证] &#FFFFFF已注册指令 &#00E5FF/" + main
                    + (aliases.isEmpty() ? "" : " &#8C8C8C(别名: " + String.join(", ", aliases) + ")"));
        } catch (Throwable throwable) {
            getLogger().warning("注册指令 /" + main + " 失败: " + throwable.getMessage());
        }
    }

    private void unregisterZbCommands() {
        if (registeredZbCommands.isEmpty()) {
            return;
        }
        CommandMap commandMap = getServer().getCommandMap();
        for (Command command : registeredZbCommands) {
            try {
                command.unregister(commandMap);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        registeredZbCommands.clear();
    }

    private void registerPlaceholderApi() {
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // ignore
            }
            expansion = null;
        }
        if (!authConfig.featurePlaceholderApi) {
            info("&#FFB300[正版认证] features.placeholder-api = false, 已跳过 PlaceholderAPI 拓展注册");
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("未检测到 PlaceholderAPI, %officialauth_xxx% 占位符不可用");
            return;
        }
        try {
            expansion = new OfficialAuthExpansion(this, authConfig, authService);
            expansion.register();
            info("&#00E5FF[正版认证] &#FFFFFF已注册 PlaceholderAPI 拓展: &#00E5FF%officialauth_status% &f等");
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "注册 PlaceholderAPI 拓展失败", throwable);
        }
    }

    // =========================================================
    //  定时任务
    // =========================================================

    private void scheduleTasks() {
        cancelTasks();

        cacheTask = getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> authService.purgeCache(),
                20L * 60L, 20L * 60L * authConfig.cachePurgeMinutes);

        if (authConfig.cleanupEnabled) {
            long period = 20L * 60L * authConfig.cleanupIntervalMinutes;
            cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!repository.canWrite()) {
                    return;
                }
                repository.cleanup();
            }, period, period);
        }
    }

    private void cancelTasks() {
        if (cacheTask != null) {
            cacheTask.cancel();
            cacheTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void checkMode() {
        boolean onlineMode = getServer().getOnlineMode();
        if (mode.isAuth() && !onlineMode) {
            getLogger().warning("当前为 AUTH(正版认证服)模式, 但 server.properties 的 online-mode = false, "
                    + "无法完成微软鉴权! 请把它改为 true 后重启。");
        }
        if (mode.isMain() && onlineMode) {
            getLogger().warning("当前为 MAIN(主服)模式, 但 online-mode = true, 主服通常应该设为 false。");
        }
        if (mode.isMain() && !authConfig.allowMainWrite) {
            info("&#8C8C8C[正版认证] 主服为只读模式(数据源唯一可信): 所有写库操作都会被拒绝。");
        }
    }

    // =========================================================
    //  热重载
    // =========================================================

    public void reloadAll() {
        String before = databaseSignature();
        reloadConfig();
        authConfig.load();
        mode = authConfig.mode;
        Console.setAnsi(authConfig.bannerAnsi);

        if (!before.equals(databaseSignature())) {
            try {
                database.init();
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "重新初始化数据库失败: " + ex.getMessage(), ex);
            }
        }

        registerZbCommands();
        registerPlaceholderApi();
        scheduleTasks();
        checkMode();
        info("&#00E5FF[正版认证] &#FFFFFF配置已重载 &#8C8C8C| 模式: &f" + mode);
    }

    private String databaseSignature() {
        return authConfig.dbHost + ':' + authConfig.dbPort + '/' + authConfig.dbName + '/'
                + authConfig.dbUser + '/' + authConfig.dbPassword + '/' + authConfig.tablePrefix
                + '/' + authConfig.poolSize;
    }

    // =========================================================
    //  Getter
    // =========================================================

    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AuthRepository getAuthRepository() {
        return repository;
    }

    public ServerMode getMode() {
        return mode;
    }

    public List<Command> getRegisteredZbCommands() {
        return Collections.unmodifiableList(registeredZbCommands);
    }
}
