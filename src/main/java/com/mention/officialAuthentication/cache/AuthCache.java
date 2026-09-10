package com.mention.officialAuthentication.cache;

import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.model.AuthStatus;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存模块：主服专用，避免同一个玩家反复查库。
 *
 * <p>正版结果缓存久一些；离线/未认证结果缓存短一些，这样玩家刚在正版服认证完，
 * 回到主服很快就能生效。</p>
 */
public final class AuthCache {

    private static final class Entry {
        private final AuthResult result;
        private final long expiresAt;

        private Entry(AuthResult result, long expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AuthConfig config;

    public AuthCache(AuthConfig config) {
        this.config = config;
    }

    public static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public AuthResult get(String name) {
        if (!config.featureCache) {
            return null;
        }
        String key = key(name);
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt <= System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.result;
    }

    public void put(AuthResult result) {
        if (result == null || !config.featureCache) {
            return;
        }
        long ttl;
        if (result.isError()) {
            ttl = config.cacheErrorSeconds * 1000L;
        } else if (result.getStatus() == AuthStatus.PREMIUM) {
            ttl = config.cacheSeconds * 1000L;
        } else {
            ttl = config.cacheOfflineSeconds * 1000L;
        }
        entries.put(key(result.getQueryName()), new Entry(result, System.currentTimeMillis() + ttl));
    }

    public void invalidate(String name) {
        if (name != null) {
            entries.remove(key(name));
        }
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public void purgeExpired() {
        if (!config.featureCache) {
            return;
        }
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }
}
