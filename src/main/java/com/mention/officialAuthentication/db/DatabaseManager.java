package com.mention.officialAuthentication.db;

import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.util.Console;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;

/**
 * 数据库模块：驱动解析 / 连接池管理 / 自动建表。
 *
 * <p>驱动获取顺序：</p>
 * <ol>
 *     <li>服务端已有驱动（Paper 的 {@code libraries}、或手动放进服务端）-> DriverManager</li>
 *     <li>插件 libs 目录 / 自动下载（Spigot 等环境同样开箱即用）-> 直接使用驱动实例</li>
 * </ol>
 *
 * <p>表结构（前缀由配置决定，默认 officialauth_）：</p>
 * <ul>
 *     <li><b>identities</b>：以正版 UUID 为根主键的正版身份表</li>
 *     <li><b>names</b>：昵称链（小写昵称为主键），一条记录只归属一个身份</li>
 *     <li><b>history</b>：认证事件流水（AUTH / RENAME / RELEASE / UNLINK）</li>
 * </ul>
 */
public final class DatabaseManager {

    private final JavaPlugin plugin;
    private final AuthConfig config;
    private SimpleConnectionPool pool;

    public DatabaseManager(JavaPlugin plugin, AuthConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public synchronized void init() throws Exception {
        close();

        String url = "jdbc:mysql://" + config.dbHost + ":" + config.dbPort + "/" + config.dbName
                + "?useUnicode=true&characterEncoding=utf8"
                + (config.extraParams.isEmpty() ? "" : "&" + config.extraParams);

        ConnectionFactory factory = resolveFactory(url);

        this.pool = new SimpleConnectionPool(factory, config.poolSize, config.connectionTimeoutMs, plugin.getLogger());

        try (Connection connection = pool.borrow()) {
            if (connection == null) {
                throw new SQLException("无法获取数据库连接");
            }
        }

        if (config.createTablesOnStart) {
            createTables();
        }

        plugin.getLogger().info(Console.color("&#00E5FF[正版认证] &#FFFFFFMySQL 连接成功 &#8C8C8C-> &f"
                + config.dbHost + ":" + config.dbPort + "/" + config.dbName
                + " &#8C8C8C(表前缀: &f" + config.tablePrefix + "&#8C8C8C)"));
    }

    /**
     * 解析出可用的连接创建方式：优先服务端自带驱动，其次插件自带的 libs / 自动下载。
     */
    private ConnectionFactory resolveFactory(String url) throws Exception {
        if (loadDriverClass("com.mysql.cj.jdbc.Driver") || loadDriverClass("com.mysql.jdbc.Driver")) {
            plugin.getLogger().info(Console.color("&#00E5FF[正版认证] &#FFFFFF使用服务端自带的 MySQL 驱动"));
            return () -> DriverManager.getConnection(url, config.dbUser, config.dbPassword);
        }

        Driver driver = LibraryLoader.loadMySqlDriver(new File(plugin.getDataFolder(), "libs"),
                plugin.getLogger(), plugin.getDescription().getVersion(), config, url);
        Properties properties = new Properties();
        properties.setProperty("user", config.dbUser);
        properties.setProperty("password", config.dbPassword);
        return () -> {
            Connection connection = driver.connect(url, properties);
            if (connection == null) {
                throw new SQLException("MySQL 驱动无法处理该连接地址: " + url);
            }
            return connection;
        };
    }

    private boolean loadDriverClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isReady() {
        SimpleConnectionPool current = pool;
        return current != null;
    }

    public Connection borrow() throws SQLException {
        SimpleConnectionPool current = pool;
        if (current == null) {
            throw new SQLException("数据库未就绪, 请检查 config.yml 的 database 配置后执行 /oauth reload");
        }
        return current.borrow();
    }

    public void release(Connection connection) {
        SimpleConnectionPool current = pool;
        if (current != null) {
            current.release(connection);
        }
    }

    public String table(String name) {
        return config.tablePrefix + name;
    }

    public void createTables() throws SQLException {
        String identities = table("identities");
        String names = table("names");
        String history = table("history");

        execute("CREATE TABLE IF NOT EXISTS `" + identities + "` ("
                + "`real_uuid` CHAR(36) NOT NULL COMMENT '正版原生UUID(根主键)',"
                + "`current_name` VARCHAR(16) DEFAULT NULL COMMENT '当前昵称',"
                + "`current_name_lower` VARCHAR(16) DEFAULT NULL COMMENT '当前昵称(小写)',"
                + "`first_auth` BIGINT NOT NULL DEFAULT 0 COMMENT '首次认证时间',"
                + "`last_auth` BIGINT NOT NULL DEFAULT 0 COMMENT '最后认证时间',"
                + "PRIMARY KEY (`real_uuid`),"
                + "KEY `idx_last_auth` (`last_auth`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正版身份表(以UUID为根)'");

        execute("CREATE TABLE IF NOT EXISTS `" + names + "` ("
                + "`name_lower` VARCHAR(16) NOT NULL COMMENT '昵称(统一小写检索)',"
                + "`name_display` VARCHAR(16) NOT NULL COMMENT '原始大小写昵称',"
                + "`real_uuid` CHAR(36) NOT NULL COMMENT '所属正版身份',"
                + "`first_seen` BIGINT NOT NULL DEFAULT 0,"
                + "`last_seen` BIGINT NOT NULL DEFAULT 0,"
                + "`active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=该身份当前昵称 0=旧昵称',"
                + "`detached_at` BIGINT DEFAULT NULL COMMENT '脱离时间',"
                + "PRIMARY KEY (`name_lower`),"
                + "KEY `idx_uuid` (`real_uuid`),"
                + "KEY `idx_active` (`active`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='昵称链(一条昵称只属于一个身份)'");

        execute("CREATE TABLE IF NOT EXISTS `" + history + "` ("
                + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                + "`real_uuid` CHAR(36) NOT NULL,"
                + "`name_lower` VARCHAR(16) NOT NULL,"
                + "`name_display` VARCHAR(16) DEFAULT NULL,"
                + "`event` VARCHAR(16) NOT NULL COMMENT 'AUTH/RENAME/RELEASE/UNLINK',"
                + "`time` BIGINT NOT NULL,"
                + "PRIMARY KEY (`id`),"
                + "KEY `idx_uuid_time` (`real_uuid`, `time`),"
                + "KEY `idx_time` (`time`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证事件流水'");
    }

    private void execute(String sql) throws SQLException {
        Connection connection = null;
        try {
            connection = borrow();
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        } finally {
            release(connection);
        }
    }

    public synchronized void close() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    public void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.WARNING, message, throwable);
    }
}
