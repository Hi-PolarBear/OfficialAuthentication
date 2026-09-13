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
 * <p>每个结果有两个时间点：</p>
 * <ul>
 *     <li><b>freshUntil</b>：新鲜期，期内直接返回缓存，不查库</li>
 *     <li><b>staleUntil</b>：兜底期，过期后仍保留旧值，
 *         异步刷新期间先用旧值对外展示（例如「正版」不会闪成「查询中 / 离线」），
 *         查询结果回来后覆盖为最新状态</li>
 * </ul>
 */
public final class AuthCache {

    private static final class Entry {
        private final AuthResult result;
        private final long freshUntil;
        private final long staleUntil;

        private Entry(AuthResult result, long freshUntil, long staleUntil) {
            this.result = result;
            this.freshUntil = freshUntil;
            this.staleUntil = staleUntil;
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

    /**
     * 取「新鲜」结果：未过期直接返回，过期返回 null（但会保留旧值供刷新期间显示）。
     */
    public AuthResult get(String name) {
        if (!config.featureCache) {
            return null;
        }
        Entry entry = entries.get(key(name));
        if (entry == null) {
            return null;
        }
        return System.currentTimeMillis() <= entry.freshUntil ? entry.result : null;
    }

    /**
     * 取「上次已知」结果：即使已过新鲜期也能拿到，用于异步刷新期间不闪状态。
     * 超过兜底期则彻底丢弃。
     */
    public AuthResult getStale(String name) {
        if (!config.featureCache) {
            return null;
        }
        String key = key(name);
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.staleUntil) {
            entries.remove(key, entry);
            return null;
        }
        return entry.result;
    }

    public void put(AuthResult result) {
        if (result == null) {
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
        store(result, ttl);
    }

    /**
     * 把某个结果继续当作「新鲜」保持一段时间（查询失败时冷却，避免频繁查库）。
     */
    public void hold(AuthResult result, long ttlMillis) {
        if (result == null) {
            return;
        }
        store(result, Math.max(1000L, ttlMillis));
    }

    private void store(AuthResult result, long ttlMillis) {
        if (!config.featureCache) {
            return;
        }
        long now = System.currentTimeMillis();
        long stale = Math.max(ttlMillis, Math.max(0, config.cacheStaleSeconds) * 1000L);
        entries.put(key(result.getQueryName()), new Entry(result, now + ttlMillis, now + stale));
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
        entries.entrySet().removeIf(entry -> entry.getValue().staleUntil <= now);
    }
}
