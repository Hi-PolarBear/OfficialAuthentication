package com.mention.officialAuthentication.db;

import com.mention.officialAuthentication.config.AuthConfig;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * 驱动自动加载器。
 *
 * <p>Paper 通过 plugin.yml 的 {@code libraries} 自动下载驱动；
 * 但 Spigot 等不支持该机制的服务端，插件会在这里自己下载
 * {@code mysql-connector-j} 到 {@code plugins/OfficialAuthentication/libs/}，
 * 再用独立类加载器加载，做到「开箱即用」。</p>
 *
 * <p>也可以手动把任意 MySQL 驱动 jar 丢进 libs 目录，插件会一并加载。</p>
 */
public final class LibraryLoader {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final String DRIVER_ENTRY = "com/mysql/cj/jdbc/Driver.class";
    private static final int MIN_JAR_SIZE = 4096;

    private static URLClassLoader libraryLoader;

    private LibraryLoader() {
    }

    /**
     * 加载（必要时下载）MySQL 驱动。
     *
     * @param libsDir    驱动存放目录（不存在会自动创建）
     * @param logger     日志
     * @param version    插件版本(仅用于下载 User-Agent)
     * @return 可直接创建连接的驱动实例
     */
    public static Driver loadMySqlDriver(File libsDir, Logger logger, String version,
                                         AuthConfig config, String connectionUrl) throws Exception {
        if (!libsDir.isDirectory() && !libsDir.mkdirs()) {
            throw new IllegalStateException("无法创建插件目录: " + libsDir.getAbsolutePath());
        }

        File target = new File(libsDir, "mysql-connector-j-" + config.driverVersion + ".jar");
        if (!hasDriverJar(libsDir)) {
            if (!config.driverAutoDownload) {
                throw new IllegalStateException("未找到 MySQL 驱动, 且已关闭自动下载(database.driver.auto-download)。"
                        + "请把 mysql-connector-j-" + config.driverVersion + ".jar 放入 " + libsDir.getAbsolutePath());
            }
            download(logger, version, config, target);
        }

        if (libraryLoader == null) {
            libraryLoader = new URLClassLoader(collectJars(libsDir), LibraryLoader.class.getClassLoader());
        }

        Class<?> driverClass;
        try {
            driverClass = Class.forName(DRIVER_CLASS, true, libraryLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("libs 目录里没有找到可用的 MySQL 驱动类, 请检查 " + libsDir.getAbsolutePath());
        }
        Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
        if (!driver.acceptsURL(connectionUrl)) {
            logger.warning("MySQL 驱动未识别该连接地址, 仍将尝试连接: " + connectionUrl);
        }
        logger.info("已加载插件自带 MySQL 驱动: " + driver.getClass().getName()
                + " (" + driver.getMajorVersion() + "." + driver.getMinorVersion() + ")");
        return driver;
    }

    /**
     * libs 目录里是否已经有包含驱动类的 jar。
     */
    private static boolean hasDriverJar(File libsDir) {
        File[] files = libsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (jarContains(file, DRIVER_ENTRY)) {
                return true;
            }
        }
        return false;
    }

    private static void download(Logger logger, String pluginVersion, AuthConfig config, File target) throws Exception {
        String relative = "com/mysql/mysql-connector-j/" + config.driverVersion
                + "/mysql-connector-j-" + config.driverVersion + ".jar";
        Exception lastError = null;

        for (String repository : config.driverRepositories) {
            String base = repository.endsWith("/") ? repository.substring(0, repository.length() - 1) : repository;
            String url = base + "/" + relative;
            File temp = new File(target.getParentFile(), target.getName() + ".tmp");

            try {
                logger.info("正在下载 MySQL 驱动: " + url);
                downloadTo(url, temp, config.driverTimeoutMs, pluginVersion);
                if (!jarContains(temp, DRIVER_ENTRY)) {
                    throw new IllegalStateException("下载的文件不是有效的 MySQL 驱动");
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("MySQL 驱动下载完成: " + target.getAbsolutePath());
                return;
            } catch (Exception ex) {
                lastError = ex;
                logger.warning("从该仓库下载失败 (" + url + "): " + ex.getMessage());
                if (temp.isFile() && !temp.delete()) {
                    temp.deleteOnExit();
                }
            }
        }

        throw new IllegalStateException("MySQL 驱动自动下载失败, 请手动把 mysql-connector-j-"
                + config.driverVersion + ".jar 放入 " + target.getParentFile().getAbsolutePath(), lastError);
    }

    private static void downloadTo(String url, File file, int timeoutMs, String pluginVersion) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "OfficialAuthentication/" + pluginVersion);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static URL[] collectJars(File libsDir) throws Exception {
        File[] files = libsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        List<URL> urls = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                urls.add(file.toURI().toURL());
            }
        }
        return urls.toArray(new URL[0]);
    }

    private static boolean jarContains(File file, String entryName) {
        if (file == null || !file.isFile() || file.length() < MIN_JAR_SIZE) {
            return false;
        }
        try (ZipFile zip = new ZipFile(file)) {
            return zip.getEntry(entryName) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 释放类加载器（仅 /oauth reload 重建连接池时使用）。
     */
    public static void reset() {
        URLClassLoader loader = libraryLoader;
        libraryLoader = null;
        if (loader != null) {
            try {
                loader.close();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger("OfficialAuthentication").log(Level.FINE, "关闭驱动类加载器失败", ex);
            }
        }
    }
}
