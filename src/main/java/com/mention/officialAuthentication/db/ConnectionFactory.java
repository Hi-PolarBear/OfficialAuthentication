package com.mention.officialAuthentication.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 连接创建策略。
 *
 * <p>服务端（Paper libraries / 手动放入）自带驱动时走 {@code DriverManager}；
 * 驱动由插件自己下载时直接用驱动实例创建连接，避免类加载器隔离问题。</p>
 */
@FunctionalInterface
public interface ConnectionFactory {

    Connection open() throws SQLException;
}
