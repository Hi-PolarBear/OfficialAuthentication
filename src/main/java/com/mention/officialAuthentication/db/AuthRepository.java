package com.mention.officialAuthentication.db;

import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.model.AuthStatus;
import com.mention.officialAuthentication.model.NameEntry;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * 数据库读写层。
 *
 * <p>所有方法都是异步的（返回 {@link CompletableFuture}），永远不会在服务端主线程上做 SQL。</p>
 *
 * <p><b>账号-身份链模型</b></p>
 * <pre>
 * identities (real_uuid 主键)
 *      ↑ real_uuid
 * names      (name_lower 主键 -> 一条昵称同一时间只归属一个身份)
 * </pre>
 * 玩家改名后：旧昵称行被标记 active=0 保留历史，新昵称行 active=1；
 * 因此同一个微软账号永远只有 1 条「正版身份」，旧 ID 不再占用正版身份。
 */
public final class AuthRepository {

    public static final String EVENT_AUTH = "AUTH";
    public static final String EVENT_RENAME = "RENAME";
    public static final String EVENT_RELEASE = "RELEASE";
    public static final String EVENT_UNLINK = "UNLINK";

    private final JavaPlugin plugin;
    private final AuthConfig config;
    private final DatabaseManager db;
    private final Executor executor;

    public AuthRepository(JavaPlugin plugin, AuthConfig config, DatabaseManager db, Executor executor) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
        this.executor = executor;
    }

    private void debug(String message) {
        if (config.debug) {
            plugin.getLogger().info("[调试] " + message);
        }
    }

    public boolean canWrite() {
        return config.mode.isAuth() || config.allowMainWrite;
    }

    // =========================================================
    //  主服：查询
    // =========================================================

    public CompletableFuture<AuthResult> lookupByName(String name) {
        final String display = name;
        final String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryByName(key, display);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "查询玩家 " + display + " 的认证信息失败: " + ex.getMessage());
                return AuthResult.error(display, ex.getMessage());
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "查询玩家 " + display + " 时出现异常", ex);
                return AuthResult.error(display, String.valueOf(ex.getMessage()));
            }
        }, executor);
    }

    private AuthResult queryByName(String key, String display) throws SQLException {
        String sql = "SELECT n.active, i.real_uuid, i.current_name, i.first_auth, i.last_auth"
                + " FROM " + db.table("names") + " n"
                + " JOIN " + db.table("identities") + " i ON i.real_uuid = n.real_uuid"
                + " WHERE n.name_lower = ? LIMIT 1";

        Connection connection = null;
        try {
            connection = db.borrow();
            String realUuid = null;
            String currentName = null;
            long firstAuth = 0L;
            long lastAuth = 0L;
            boolean found = false;
            boolean active = false;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(config.queryTimeoutSeconds);
                statement.setString(1, key);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        found = true;
                        active = rs.getInt("active") == 1;
                        realUuid = rs.getString("real_uuid");
                        currentName = rs.getString("current_name");
                        firstAuth = rs.getLong("first_auth");
                        lastAuth = rs.getLong("last_auth");
                    }
                }
            }

            if (!found) {
                debug("查询 " + display + " -> 无认证记录");
                return AuthResult.unknown(display);
            }

            long expireAt = config.featureAuthExpiry ? lastAuth + config.expireMillis : 0L;
            AuthStatus status;
            if (!active) {
                status = AuthStatus.FORMER;
            } else if (expireAt > 0L && System.currentTimeMillis() > expireAt) {
                status = AuthStatus.EXPIRED;
            } else {
                status = AuthStatus.PREMIUM;
            }

            List<NameEntry> names = config.fetchNameHistory
                    ? queryNames(connection, realUuid)
                    : Collections.emptyList();

            debug("查询 " + display + " -> " + status + " (uuid=" + realUuid + ")");
            return new AuthResult(display, status, realUuid, currentName, firstAuth, lastAuth, expireAt, names, null);
        } finally {
            db.release(connection);
        }
    }

    private List<NameEntry> queryNames(Connection connection, String realUuid) throws SQLException {
        List<NameEntry> list = new ArrayList<>();
        String sql = "SELECT name_display, active, last_seen FROM " + db.table("names")
                + " WHERE real_uuid = ? ORDER BY active DESC, last_seen DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(config.queryTimeoutSeconds);
            statement.setString(1, realUuid);
            statement.setInt(2, Math.max(1, config.maxHistoryNames));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new NameEntry(rs.getString("name_display"), rs.getInt("active") == 1, rs.getLong("last_seen")));
                }
            }
        }
        return list;
    }

    // =========================================================
    //  正版服：写入 / 覆盖更新
    // =========================================================

    public CompletableFuture<Boolean> recordAuthentication(UUID realUuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            if (!canWrite()) {
                plugin.getLogger().warning("主服模式禁止写入认证数据, 已忽略玩家 " + name + " 的认证请求。"
                        + "(如需主服也可写入, 请设置 auth.allow-main-write: true)");
                return false;
            }
            if (!config.featureAuthRecord) {
                debug("features.auth-record = false, 已跳过写入 " + name);
                return false;
            }
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    doRecord(realUuid, name);
                    return true;
                } catch (SQLException ex) {
                    if (attempt == 1 && isRetryable(ex)) {
                        debug("写入出现可重试错误, 正在重试: " + ex.getMessage());
                        continue;
                    }
                    plugin.getLogger().log(Level.WARNING, "写入玩家 " + name + " 的认证信息失败: " + ex.getMessage());
                    return false;
                }
            }
            return false;
        }, executor);
    }

    /**
     * 并发抢占昵称时 MySQL 可能抛出死锁/锁等待超时，这种情况重试一次即可。
     */
    private boolean isRetryable(SQLException ex) {
        int code = ex.getErrorCode();
        return code == 1213 || code == 1205 || ex instanceof java.sql.SQLTransientException;
    }

    /**
     * 覆盖更新认证记录（事务）：
     * <ol>
     *     <li>若该昵称目前归属另一个身份 -> 原身份释放该昵称（RELEASE）</li>
     *     <li>若本身份改名 -> 旧昵称行标记 active=0（RENAME）</li>
     *     <li>身份表按 real_uuid upsert，昵称表按小写昵称 upsert</li>
     * </ol>
     */
    private void doRecord(UUID realUuid, String name) throws SQLException {
        final String uuid = realUuid.toString();
        final String lower = name.toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();

        Connection connection = null;
        try {
            connection = db.borrow();
            connection.setAutoCommit(false);
            try {
                // 1) 该昵称当前归谁
                String owner = queryString(connection,
                        "SELECT real_uuid FROM " + db.table("names") + " WHERE name_lower = ? FOR UPDATE", lower);

                // 2) 本身份上一次使用的昵称
                String previous = queryString(connection,
                        "SELECT current_name_lower FROM " + db.table("identities") + " WHERE real_uuid = ? FOR UPDATE", uuid);

                if (owner != null && !owner.equals(uuid)) {
                    // 昵称被别的身份占用（对方改名或账号易手）—— 抢占
                    String ownerCurrent = queryString(connection,
                            "SELECT current_name_lower FROM " + db.table("identities") + " WHERE real_uuid = ?", owner);
                    if (lower.equals(ownerCurrent)) {
                        update(connection, "UPDATE " + db.table("identities")
                                        + " SET current_name = NULL, current_name_lower = NULL"
                                        + " WHERE real_uuid = ? AND current_name_lower = ?",
                                owner, lower);
                    }
                    update(connection, "UPDATE " + db.table("names")
                                    + " SET active = 0, detached_at = ? WHERE name_lower = ? AND active = 1",
                            now, lower);
                    insertHistory(connection, owner, lower, null, EVENT_RELEASE, now);
                    debug("昵称 " + lower + " 从身份 " + owner + " 转移到 " + uuid);
                }

                if (previous != null && !previous.equals(lower)) {
                    if (!config.featureNameChain) {
                        // 关闭身份链 / 改名追踪: 直接忘掉旧昵称（旧ID 不再保留任何记录）
                        update(connection, "DELETE FROM " + db.table("names")
                                + " WHERE real_uuid = ? AND name_lower <> ?", uuid, lower);
                        debug("features.name-chain = false, 已移除 " + uuid + " 的旧昵称记录");
                    } else if (config.featureLegacyInvalidate) {
                        // 本身份改名：旧昵称不再占用正版身份，但保留在昵称链里
                        update(connection, "UPDATE " + db.table("names")
                                        + " SET active = 0, detached_at = ? WHERE name_lower = ? AND active = 1",
                                now, previous);
                    }
                    insertHistory(connection, uuid, previous, null, EVENT_RENAME, now);
                    debug("身份 " + uuid + " 改名: " + previous + " -> " + lower);
                }

                // 3) 身份表（以正版 UUID 为根，唯一）
                update(connection,
                        "INSERT INTO " + db.table("identities")
                                + " (real_uuid, current_name, current_name_lower, first_auth, last_auth)"
                                + " VALUES (?, ?, ?, ?, ?)"
                                + " ON DUPLICATE KEY UPDATE current_name = VALUES(current_name),"
                                + " current_name_lower = VALUES(current_name_lower), last_auth = VALUES(last_auth)",
                        uuid, name, lower, now, now);

                // 4) 昵称表（以昵称为键；被别人占用时重置首次记录时间）
                update(connection,
                        "INSERT INTO " + db.table("names")
                                + " (name_lower, name_display, real_uuid, first_seen, last_seen, active, detached_at)"
                                + " VALUES (?, ?, ?, ?, ?, 1, NULL)"
                                + " ON DUPLICATE KEY UPDATE"
                                + " first_seen = IF(real_uuid = VALUES(real_uuid), first_seen, VALUES(first_seen)),"
                                + " name_display = VALUES(name_display),"
                                + " real_uuid = VALUES(real_uuid),"
                                + " last_seen = VALUES(last_seen),"
                                + " active = 1,"
                                + " detached_at = NULL",
                        lower, name, uuid, now, now);

                insertHistory(connection, uuid, lower, name, EVENT_AUTH, now);

                connection.commit();
                debug("认证写入成功: " + name + " (" + uuid + ")");
            } catch (SQLException ex) {
                safeRollback(connection);
                throw ex;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // 交由连接池在回收时处理
                }
            }
        } finally {
            db.release(connection);
        }
    }

    // =========================================================
    //  解绑
    // =========================================================

    public CompletableFuture<Boolean> unlink(String name) {
        final String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> {
            if (!canWrite()) {
                return false;
            }
            Connection connection = null;
            try {
                connection = db.borrow();
                connection.setAutoCommit(false);
                try {
                    String uuid = queryString(connection,
                            "SELECT real_uuid FROM " + db.table("names") + " WHERE name_lower = ? FOR UPDATE", lower);
                    if (uuid == null) {
                        safeRollback(connection);
                        return false;
                    }
                    update(connection, "UPDATE " + db.table("identities")
                                    + " SET current_name = NULL, current_name_lower = NULL"
                                    + " WHERE real_uuid = ? AND current_name_lower = ?",
                            uuid, lower);
                    update(connection, "DELETE FROM " + db.table("names") + " WHERE name_lower = ?", lower);
                    int left = count(connection, "SELECT COUNT(*) FROM " + db.table("names") + " WHERE real_uuid = ?", uuid);
                    if (left == 0) {
                        update(connection, "DELETE FROM " + db.table("identities") + " WHERE real_uuid = ?", uuid);
                    }
                    insertHistory(connection, uuid, lower, name, EVENT_UNLINK, System.currentTimeMillis());
                    connection.commit();
                    return true;
                } catch (SQLException ex) {
                    safeRollback(connection);
                    throw ex;
                } finally {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // ignore
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "解除 " + name + " 的正版绑定失败: " + ex.getMessage());
                return false;
            } finally {
                db.release(connection);
            }
        }, executor);
    }

    // =========================================================
    //  定时清理
    // =========================================================

    public CompletableFuture<Integer> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis();
            long expireBefore = now - config.expireMillis;
            int affected = 0;
            try {
                if (!config.featureAuthExpiry) {
                    debug("features.auth-expiry = false, 跳过基于有效期的清理");
                } else if (config.cleanupDeleteExpired) {
                    affected += updateAuto("DELETE FROM " + db.table("names")
                            + " WHERE real_uuid IN (SELECT real_uuid FROM " + db.table("identities") + " WHERE last_auth < ?)", expireBefore);
                    affected += updateAuto("DELETE FROM " + db.table("identities") + " WHERE last_auth < ?", expireBefore);
                } else {
                    affected += updateAuto("UPDATE " + db.table("names") + " n JOIN " + db.table("identities")
                                    + " i ON i.real_uuid = n.real_uuid"
                                    + " SET n.active = 0, n.detached_at = ?"
                                    + " WHERE n.active = 1 AND i.last_auth < ?",
                            now, expireBefore);
                    affected += updateAuto("UPDATE " + db.table("identities")
                                    + " SET current_name = NULL, current_name_lower = NULL"
                                    + " WHERE last_auth < ? AND current_name_lower IS NOT NULL",
                            expireBefore);
                }
                if (config.historyEnabled && config.historyRetainDays > 0) {
                    long retainBefore = now - config.historyRetainDays * 24L * 3600L * 1000L;
                    affected += updateAuto("DELETE FROM " + db.table("history") + " WHERE time < ?", retainBefore);
                }
                debug("清理完成, 受影响 " + affected + " 条");
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "清理过期认证数据失败: " + ex.getMessage());
                return -1;
            }
            return affected;
        }, executor);
    }

    // =========================================================
    //  工具
    // =========================================================

    private int updateAuto(String sql, Object... args) throws SQLException {
        Connection connection = null;
        try {
            connection = db.borrow();
            return update(connection, sql, args);
        } finally {
            db.release(connection);
        }
    }

    private String queryString(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(config.queryTimeoutSeconds);
            applyArgs(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int count(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(config.queryTimeoutSeconds);
            applyArgs(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(config.queryTimeoutSeconds);
            applyArgs(statement, args);
            return statement.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, String uuid, String nameLower, String nameDisplay,
                               String event, long time) throws SQLException {
        if (!config.historyEnabled) {
            return;
        }
        update(connection,
                "INSERT INTO " + db.table("history") + " (real_uuid, name_lower, name_display, event, time)"
                        + " VALUES (?, ?, ?, ?, ?)",
                uuid, nameLower, nameDisplay == null ? nameLower : nameDisplay, event, time);
    }

    private void applyArgs(PreparedStatement statement, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    private void safeRollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.FINE, "回滚失败", ex);
        }
    }
}
