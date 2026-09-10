package com.mention.officialAuthentication.model;

/**
 * 昵称链上的一条记录。
 */
public final class NameEntry {

    private final String name;
    private final boolean current;
    private final long lastSeen;

    public NameEntry(String name, boolean current, long lastSeen) {
        this.name = name;
        this.current = current;
        this.lastSeen = lastSeen;
    }

    public String getName() {
        return name;
    }

    /** 是否是该正版身份当前正在使用的昵称 */
    public boolean isCurrent() {
        return current;
    }

    public long getLastSeen() {
        return lastSeen;
    }
}
