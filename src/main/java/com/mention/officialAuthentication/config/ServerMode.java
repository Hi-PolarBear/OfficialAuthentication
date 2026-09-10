package com.mention.officialAuthentication.config;

import java.util.Locale;

/**
 * 服务端角色。
 */
public enum ServerMode {

    /** 正版认证服 (online-mode: true) —— 唯一允许写库的一端 */
    AUTH,
    /** 主服 (online-mode: false) —— 只读，只做识别展示 */
    MAIN;

    public static ServerMode parse(String raw) {
        if (raw == null) {
            return MAIN;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return MAIN;
        }
        if (value.equals("auth") || value.equals("premium") || value.equals("verify")
                || value.contains("正版") || value.contains("认证") || value.contains("子服")) {
            return AUTH;
        }
        return MAIN;
    }

    public boolean isAuth() {
        return this == AUTH;
    }

    public boolean isMain() {
        return this == MAIN;
    }
}
