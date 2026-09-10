package com.mention.officialAuthentication.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次认证查询的结果（不可变）。
 */
public final class AuthResult {

    private final String queryName;
    private final AuthStatus status;
    private final String realUuid;
    private final String currentName;
    private final long firstAuth;
    private final long lastAuth;
    private final long expireAt;
    private final List<NameEntry> names;
    private final String error;

    public AuthResult(String queryName, AuthStatus status, String realUuid, String currentName,
                      long firstAuth, long lastAuth, long expireAt, List<NameEntry> names, String error) {
        this.queryName = queryName;
        this.status = status;
        this.realUuid = realUuid;
        this.currentName = currentName;
        this.firstAuth = firstAuth;
        this.lastAuth = lastAuth;
        this.expireAt = expireAt;
        this.names = names == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(names));
        this.error = error;
    }

    public static AuthResult unknown(String queryName) {
        return new AuthResult(queryName, AuthStatus.UNKNOWN, null, null, 0L, 0L, 0L, Collections.emptyList(), null);
    }

    public static AuthResult error(String queryName, String message) {
        return new AuthResult(queryName, AuthStatus.UNKNOWN, null, null, 0L, 0L, 0L, Collections.emptyList(),
                message == null ? "未知错误" : message);
    }

    public String getQueryName() {
        return queryName;
    }

    public AuthStatus getStatus() {
        return status;
    }

    public String getRealUuid() {
        return realUuid;
    }

    public String getCurrentName() {
        return currentName;
    }

    public long getFirstAuth() {
        return firstAuth;
    }

    public long getLastAuth() {
        return lastAuth;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public List<NameEntry> getNames() {
        return names;
    }

    public String getError() {
        return error;
    }

    /** 查询是否出错（数据库异常等） */
    public boolean isError() {
        return error != null;
    }

    /** 是否被判定为正版 */
    public boolean isPremium() {
        return status == AuthStatus.PREMIUM;
    }

    /** 数据库里是否存在这个昵称的记录 */
    public boolean isKnown() {
        return status != AuthStatus.UNKNOWN;
    }

    public long getRemainingMillis() {
        if (expireAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, expireAt - System.currentTimeMillis());
    }

    /** 全部昵称，当前使用的排在最前 */
    public List<String> nameList() {
        List<String> list = new ArrayList<>(names.size());
        for (NameEntry entry : names) {
            list.add(entry.getName());
        }
        return list;
    }

    @Override
    public String toString() {
        return "AuthResult{name=" + queryName + ", status=" + status + ", uuid=" + realUuid + ", error=" + error + "}";
    }
}
