package com.mention.officialAuthentication.cache;

import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.db.AuthRepository;
import com.mention.officialAuthentication.model.AuthResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 统一入口：先查缓存，未命中再异步查库；同一个昵称并发查询只会发出一次 SQL。
 */
public final class AuthService {

    private final AuthConfig config;
    private final AuthRepository repository;
    private final AuthCache cache;
    private final Map<String, CompletableFuture<AuthResult>> inFlight = new ConcurrentHashMap<>();

    public AuthService(AuthConfig config, AuthRepository repository, AuthCache cache) {
        this.config = config;
        this.repository = repository;
        this.cache = cache;
    }

    public AuthResult getCached(String name) {
        if (!config.featureMainRecognition) {
            return null;
        }
        return cache.get(name);
    }

    public boolean isLoading(String name) {
        if (!config.featureMainRecognition) {
            return false;
        }
        return inFlight.containsKey(AuthCache.key(name));
    }

    public boolean recognitionEnabled() {
        return config.featureMainRecognition;
    }

    public int cacheSize() {
        return cache.size();
    }

    public void invalidate(String name) {
        cache.invalidate(name);
    }

    public void clearCache() {
        cache.clear();
    }

    public void purgeCache() {
        cache.purgeExpired();
    }

    /**
     * 阻塞式解析（只在异步线程里调用，例如 AsyncPlayerPreLoginEvent）。
     */
    public AuthResult resolveBlocking(String name, long timeoutMillis) {
        if (!config.featureMainRecognition) {
            return AuthResult.unknown(name);
        }
        AuthResult cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        try {
            return resolveAsync(name).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 异步解析，结果自动写入缓存。
     * 若 features.main-recognition = false，则直接返回「离线」，不查数据库。
     */
    public CompletableFuture<AuthResult> resolveAsync(String name) {
        final String key = AuthCache.key(name);
        if (!config.featureMainRecognition) {
            return CompletableFuture.completedFuture(AuthResult.unknown(name));
        }
        AuthResult cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<AuthResult> existing = inFlight.get(key);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<AuthResult> promise = new CompletableFuture<>();
        CompletableFuture<AuthResult> previous = inFlight.putIfAbsent(key, promise);
        if (previous != null) {
            return previous;
        }

        repository.lookupByName(name).whenComplete((result, error) -> {
            inFlight.remove(key);
            AuthResult value = result;
            if (error != null || value == null) {
                value = AuthResult.error(name, error == null ? "未知错误" : String.valueOf(error.getMessage()));
            }
            cache.put(value);
            promise.complete(value);
        });
        return promise;
    }

    public long queryTimeoutMillis() {
        return config.queryTimeoutSeconds * 1000L;
    }
}
