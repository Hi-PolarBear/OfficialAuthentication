package com.mention.officialAuthentication.model;

/**
 * 一次认证（绑定）写入的结果，用于决定给玩家什么样的反馈。
 */
public final class BindResult {

    private final boolean success;
    private final boolean firstBind;
    private final boolean renamed;
    private final String name;
    private final String previousName;
    private final String realUuid;
    private final long time;
    private final String error;

    private BindResult(boolean success, boolean firstBind, boolean renamed, String name,
                       String previousName, String realUuid, long time, String error) {
        this.success = success;
        this.firstBind = firstBind;
        this.renamed = renamed;
        this.name = name;
        this.previousName = previousName;
        this.realUuid = realUuid;
        this.time = time;
        this.error = error;
    }

    public static BindResult success(boolean firstBind, boolean renamed, String name,
                                     String previousName, String realUuid, long time) {
        return new BindResult(true, firstBind, renamed, name, previousName, realUuid, time, null);
    }

    public static BindResult failure(String error) {
        return new BindResult(false, false, false, null, null, null, 0L, error);
    }

    public boolean isSuccess() {
        return success;
    }

    /** 该正版身份是否第一次绑定 */
    public boolean isFirstBind() {
        return firstBind;
    }

    /** 是否发生改名换绑 */
    public boolean isRenamed() {
        return renamed;
    }

    public String getName() {
        return name;
    }

    public String getPreviousName() {
        return previousName;
    }

    public String getRealUuid() {
        return realUuid;
    }

    public long getTime() {
        return time;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "BindResult{success=" + success + ", firstBind=" + firstBind + ", renamed=" + renamed
                + ", name=" + name + ", previous=" + previousName + ", error=" + error + "}";
    }
}
