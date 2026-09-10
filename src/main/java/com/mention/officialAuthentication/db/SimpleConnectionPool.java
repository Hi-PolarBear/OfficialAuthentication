package com.mention.officialAuthentication.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 轻量连接池：避免每次查询都新建 MySQL 连接，也不需要额外的第三方依赖。
 *
 * <p>连接的实际创建方式由 {@link ConnectionFactory} 决定，因此既支持服务端自带驱动，
 * 也支持插件自行下载后加载的驱动。</p>
 */
public final class SimpleConnectionPool {

    private final ConnectionFactory factory;
    private final int maxSize;
    private final long borrowTimeoutMs;
    private final Logger logger;

    private final BlockingQueue<Connection> idle = new LinkedBlockingQueue<>();
    private final AtomicInteger created = new AtomicInteger();
    private volatile boolean closed;

    public SimpleConnectionPool(ConnectionFactory factory, int maxSize, long borrowTimeoutMs, Logger logger) {
        this.factory = factory;
        this.maxSize = Math.max(1, maxSize);
        this.borrowTimeoutMs = borrowTimeoutMs;
        this.logger = logger;
    }

    public Connection borrow() throws SQLException {
        if (closed) {
            throw new SQLException("连接池已关闭");
        }
        long deadline = System.currentTimeMillis() + borrowTimeoutMs;
        while (true) {
            Connection connection = idle.poll();
            if (connection != null) {
                if (isUsable(connection)) {
                    return connection;
                }
                discard(connection);
                continue;
            }
            if (created.get() < maxSize) {
                created.incrementAndGet();
                try {
                    return open();
                } catch (SQLException ex) {
                    created.decrementAndGet();
                    throw ex;
                }
            }
            long wait = deadline - System.currentTimeMillis();
            if (wait <= 0L) {
                throw new SQLException("获取数据库连接超时(" + borrowTimeoutMs + "ms), 请检查数据库状态或调大 pool-size");
            }
            try {
                connection = idle.poll(wait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SQLException("等待数据库连接时被中断");
            }
            if (connection != null) {
                if (isUsable(connection)) {
                    return connection;
                }
                discard(connection);
            }
        }
    }

    public void release(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (connection.isClosed()) {
                created.decrementAndGet();
                return;
            }
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            discard(connection);
            return;
        }
        if (closed || !idle.offer(connection)) {
            discard(connection);
        }
    }

    public synchronized void close() {
        closed = true;
        List<Connection> remaining = new ArrayList<>();
        idle.drainTo(remaining);
        for (Connection connection : remaining) {
            closeQuietly(connection);
        }
        created.set(0);
    }

    private Connection open() throws SQLException {
        return factory.open();
    }

    private boolean isUsable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(1);
        } catch (SQLException ex) {
            return false;
        }
    }

    private void discard(Connection connection) {
        created.decrementAndGet();
        closeQuietly(connection);
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.FINE, "关闭连接失败", ex);
        }
    }
}
