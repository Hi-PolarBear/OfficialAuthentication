package com.mention.officialAuthentication.model;

/**
 * 认证状态。
 */
public enum AuthStatus {

    /** 已认证且在有效期内 —— 唯一被判定为「正版」的状态 */
    PREMIUM,
    /** 有认证记录，但已超过有效期，需要重新进正版认证服刷新 */
    EXPIRED,
    /** 该昵称曾是某个正版身份的昵称，但身份已改名 / 昵称已被他人接管 —— 不再占用正版身份 */
    FORMER,
    /** 数据库里完全没有这个昵称的认证记录 */
    UNKNOWN
}
