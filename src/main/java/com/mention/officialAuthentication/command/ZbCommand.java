package com.mention.officialAuthentication.command;

import com.mention.officialAuthentication.OfficialAuthentication;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.util.Msg;
import com.mention.officialAuthentication.util.PlaceholderValues;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /zb 指令：给玩家一条聊天框提示，内容完全由 config.yml 的 command.messages 控制。
 *
 * <ul>
 *     <li>/zb —— 查询自己</li>
 *     <li>/zb &lt;玩家&gt; —— 查询他人（需要 officialauth.zb.others 权限）</li>
 * </ul>
 */
public final class ZbCommand extends Command {

    private final OfficialAuthentication plugin;
    private final AuthConfig config;
    private final AuthService service;

    public ZbCommand(String name, List<String> aliases, OfficialAuthentication plugin,
                     AuthConfig config, AuthService service) {
        super(name, config.zbDescription, "/" + name + " [玩家]", aliases == null ? new ArrayList<>() : aliases);
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!config.zbEnabled) {
            Msg.simple(sender, config, config.msgNoPermission);
            return true;
        }
        if (config.zbPermissionRequired && !sender.hasPermission(config.zbPermission)) {
            Msg.simple(sender, config, config.msgNoPermission);
            return true;
        }

        String target;
        if (args.length >= 1) {
            if (!sender.hasPermission(config.zbOthersPermission)) {
                Msg.simple(sender, config, config.msgNoPermission);
                return true;
            }
            target = args[0];
        } else {
            if (!(sender instanceof Player)) {
                Msg.simple(sender, config, config.msgPlayerOnly);
                return true;
            }
            target = sender.getName();
        }

        final String targetName = target;
        // 异步查询, 查到之后回主线程发消息
        service.resolveAsync(targetName).whenComplete((result, error) -> {
            final AuthResult finalResult = result != null
                    ? result
                    : AuthResult.error(targetName, error == null ? "未知错误" : String.valueOf(error.getMessage()));
            plugin.getServer().getScheduler().runTask(plugin, () -> sendInfo(sender, targetName, finalResult));
        });
        return true;
    }

    private void sendInfo(CommandSender sender, String targetName, AuthResult result) {
        List<String> messages = config.zbMessagesFor(PlaceholderValues.stateKey(config, result));
        if (messages.isEmpty()) {
            return;
        }
        Map<String, String> values = PlaceholderValues.build(config, plugin.getMode(),
                plugin.getServer().getOnlineMode(), targetName, result, service.isLoading(targetName));
        // 只有查询自己时才解析 PAPI，避免把被查玩家的数据替换成查询者自己的数据
        boolean applyPapi = sender instanceof Player && targetName.equalsIgnoreCase(sender.getName());
        for (String line : messages) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            Msg.raw(sender, line, values, applyPapi);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(config.zbOthersPermission)) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                }
            }
            return names;
        }
        return Collections.emptyList();
    }
}
