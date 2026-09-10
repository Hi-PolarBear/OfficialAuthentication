package com.mention.officialAuthentication.command;

import com.mention.officialAuthentication.OfficialAuthentication;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.db.AuthRepository;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.model.AuthStatus;
import com.mention.officialAuthentication.model.NameEntry;
import com.mention.officialAuthentication.util.ColorUtil;
import com.mention.officialAuthentication.util.Msg;
import com.mention.officialAuthentication.util.PlaceholderValues;
import com.mention.officialAuthentication.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /officialauth (别名 /oauth) —— 管理指令。
 */
public final class OfficialAuthCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help", "status", "info", "history", "unlink", "cleanup", "reload");

    private final OfficialAuthentication plugin;
    private final AuthService service;
    private final AuthRepository repository;

    public OfficialAuthCommand(OfficialAuthentication plugin, AuthService service, AuthRepository repository) {
        this.plugin = plugin;
        this.service = service;
        this.repository = repository;
    }

    private AuthConfig config() {
        return plugin.getAuthConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("officialauth.admin")) {
            Msg.simple(sender, config(), config().msgNoPermission);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                sendStatus(sender);
                return true;
            case "reload":
                plugin.reloadAll();
                Msg.prefixed(sender, config(), config().msgReload, valuesOf("mode", plugin.getMode().name()));
                return true;
            case "info":
                if (!requireName(sender, args, label, "info")) {
                    return true;
                }
                queryAndSend(sender, args[1], false);
                return true;
            case "history":
                if (!requireName(sender, args, label, "history")) {
                    return true;
                }
                queryAndSend(sender, args[1], true);
                return true;
            case "unlink":
                if (!requireName(sender, args, label, "unlink")) {
                    return true;
                }
                doUnlink(sender, args[1]);
                return true;
            case "cleanup":
                doCleanup(sender);
                return true;
            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private boolean requireName(CommandSender sender, String[] args, String label, String sub) {
        if (args.length >= 2) {
            return true;
        }
        Msg.simple(sender, config(), "&c用法: /" + label + " " + sub + " <玩家>");
        return false;
    }

    private void sendHelp(CommandSender sender, String label) {
        Map<String, String> values = valuesOf("label", label);
        for (String line : config().msgHelp) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            Msg.raw(sender, line, values);
        }
    }

    private void sendStatus(CommandSender sender) {
        AuthConfig config = config();
        sender.sendMessage(ColorUtil.color(config.msgPrefix + "&f运行状态"));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f模式: &b" + plugin.getMode().name()
                + " &7(服务器 online-mode: " + plugin.getServer().getOnlineMode() + ")"));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f数据库: "
                + (plugin.getDatabase().isReady() ? "&a已连接" : "&c未就绪")
                + " &7(" + config.dbHost + ":" + config.dbPort + "/" + config.dbName + ", 前缀 " + config.tablePrefix + ")"));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f认证有效期: &b" + config.expireText()
                + " &7| 缓存: &b" + service.cacheSize() + " &7条"));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f写库权限: "
                + (repository.canWrite() ? "&a允许(AUTH)" : "&e只读(MAIN)")));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &fPlaceholderAPI: "
                + (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null ? "&a已安装" : "&c未安装")));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f已启用功能: &f" + config.enabledFeatures()));
        sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f已关闭功能: &c" + config.disabledFeatures()));
    }

    private void queryAndSend(CommandSender sender, String name, boolean historyOnly) {
        service.resolveAsync(name).whenComplete((result, error) -> {
            final AuthResult value = result != null
                    ? result
                    : AuthResult.error(name, error == null ? "未知错误" : String.valueOf(error.getMessage()));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                AuthConfig config = config();
                if (value.getStatus() == AuthStatus.UNKNOWN && !value.isError()) {
                    Msg.prefixed(sender, config, config.msgNotFound, valuesOf("player", name));
                    return;
                }
                if (value.isError()) {
                    Msg.prefixed(sender, config, config.msgDbError, valuesOf("error", value.getError()));
                    return;
                }
                Map<String, String> values = PlaceholderValues.build(config, plugin.getMode(),
                        plugin.getServer().getOnlineMode(), name, value, false);
                // 管理员能看到真实状态(旧昵称不会被显示成离线)
                sender.sendMessage(ColorUtil.color(config.msgPrefix + "&f玩家 &b" + name + " &f的认证信息"));
                sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f状态: "
                        + PlaceholderValues.rawStatusText(config, value)
                        + (PlaceholderValues.legacyAsOffline(config, value) ? " &8(对外占位符按离线处理)" : "")));
                sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f正版UUID: &7" + values.get("real_uuid")));
                sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f当前昵称: &b" + values.get("current_name")));
                sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f首次认证: &7" + values.get("first_auth")));
                sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f最后认证: &7" + values.get("last_auth")
                        + " &7(剩余 " + values.get("expire_in") + ")"));
                if (historyOnly) {
                    sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f改名历史:"));
                    List<NameEntry> entries = value.getNames();
                    if (entries.isEmpty()) {
                        sender.sendMessage(ColorUtil.color("  &7暂无记录"));
                    }
                    for (NameEntry entry : entries) {
                        sender.sendMessage(ColorUtil.color("  &8- &b" + entry.getName()
                                + (entry.isCurrent() ? " &a(当前)" : " &7(旧昵称)")
                                + " &8" + TimeUtil.format(entry.getLastSeen(), config.dateFormat)));
                    }
                } else {
                    sender.sendMessage(ColorUtil.color("&#00E5FF▎ &f昵称链: &7" + values.get("names")));
                }
            });
        });
    }

    private void doUnlink(CommandSender sender, String name) {
        AuthConfig config = config();
        if (!repository.canWrite()) {
            Msg.prefixed(sender, config, config.msgMainReadOnly, null);
            return;
        }
        repository.unlink(name).whenComplete((success, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (Boolean.TRUE.equals(success)) {
                service.invalidate(name);
                Msg.prefixed(sender, config, config.msgUnlinkSuccess, valuesOf("player", name));
            } else {
                Msg.prefixed(sender, config, config.msgUnlinkFailed, valuesOf("player", name));
            }
        }));
    }

    private void doCleanup(CommandSender sender) {
        AuthConfig config = config();
        if (!repository.canWrite()) {
            Msg.prefixed(sender, config, config.msgMainReadOnly, null);
            return;
        }
        Msg.prefixed(sender, config, "&f正在清理过期认证数据...", null);
        repository.cleanup().whenComplete((affected, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (affected == null || affected < 0) {
                Msg.prefixed(sender, config, config.msgDbError,
                        valuesOf("error", error == null ? "清理失败" : String.valueOf(error.getMessage())));
                return;
            }
            Msg.prefixed(sender, config, config.msgCleanup, valuesOf("count", String.valueOf(affected)));
        }));
    }

    private static Map<String, String> valuesOf(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("officialauth.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("unlink"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}
