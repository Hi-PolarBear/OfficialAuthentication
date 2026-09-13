package com.mention.officialAuthentication.feature;

import com.mention.officialAuthentication.OfficialAuthentication;
import com.mention.officialAuthentication.cache.AuthService;
import com.mention.officialAuthentication.config.AuthConfig;
import com.mention.officialAuthentication.model.AuthResult;
import com.mention.officialAuthentication.util.Msg;
import com.mention.officialAuthentication.util.PlaceholderValues;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【实验性】玩家名前缀：在聊天与 TAB 玩家列表的 ID <b>前面追加</b>正版/离线标识。
 *
 * <p>设计原则是<b>只追加、不覆盖</b>，具体做法：</p>
 * <ul>
 *     <li>TAB：读取玩家当前的列表名（其他插件/原版设置的内容），在我们上次加过的前缀先去掉，
 *         再把新前缀拼到最前面 —— 所以别的插件加的前缀都会原样保留</li>
 *     <li>聊天：只把内容插入到消息格式的 {@code %1$s}（发言者名字）之前，
 *         格式里原有的称号、前缀等一律不动</li>
 *     <li>标识没有变化时不会重复下发，避免刷包</li>
 * </ul>
 */
public final class NamePrefixService {

    /** 玩家名前的左括号，前缀会插到它们外面 */
    private static final String OPENING_BRACKETS = "<[({【「《〔（［｛";
    /** 名字前面的颜色/格式代码与空白（插入位置要跳过它们） */
    private static final Pattern TRAILING_DECOR = Pattern.compile("(?i)(?:[&\u00a7][0-9a-fk-orx]|\\s)+$");

    private final OfficialAuthentication plugin;
    private final AuthConfig config;
    private final AuthService service;

    /** 每个玩家上一次加过的 TAB 前缀，用于"先去掉旧前缀再拼新的"以及变更检测 */
    private final Map<UUID, String> appliedTabPrefix = new HashMap<>();

    private BukkitTask task;

    public NamePrefixService(OfficialAuthentication plugin, AuthConfig config, AuthService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    // =========================================================
    //  生命周期
    // =========================================================

    public void start() {
        stop();
        if (!config.featureNamePrefix || !config.namePrefixTab) {
            return;
        }
        long interval = Math.max(1L, config.namePrefixUpdateTicks);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        appliedTabPrefix.clear();
    }

    public void forget(Player player) {
        appliedTabPrefix.remove(player.getUniqueId());
    }

    public boolean tabEnabled() {
        return config.featureNamePrefix && config.namePrefixTab;
    }

    public boolean chatEnabled() {
        return config.featureNamePrefix && config.namePrefixChat;
    }

    // =========================================================
    //  聊天
    // =========================================================

    /**
     * 把前缀插入到聊天格式里发言者名字（{@code %1$s}）之前。
     * <p>若名字前面有左括号（如原版 {@code <%1$s>}），前缀会插到括号<b>外面</b>；
     * 格式里没有 {@code %1$s} 或前缀为空时原样返回，绝不破坏别人的格式。</p>
     * <p>若玩家已经有原版计分板团队前缀/后缀（且开启了 skip-team-prefix），则<b>完全不处理</b>，
     * 以免顶掉原版聊天前缀。</p>
     */
    public void applyChatFormat(AsyncPlayerChatEvent event) {
        if (!chatEnabled() || shouldSkipForTeam(event.getPlayer())) {
            return;
        }
        String prefix = buildPrefix(config.namePrefixChatText, event.getPlayer(), true);
        event.setFormat(injectChatPrefix(event.getFormat(), prefix));
    }

    /**
     * 玩家是否已经有原版/其他插件通过计分板团队设置的前缀或后缀。
     */
    public boolean hasTeamDecoration(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Team team = player.getScoreboard().getEntryTeam(player.getName());
            if (team == null) {
                ScoreboardManager manager = plugin.getServer().getScoreboardManager();
                if (manager != null) {
                    team = manager.getMainScoreboard().getEntryTeam(player.getName());
                }
            }
            if (team == null) {
                return false;
            }
            String prefix = team.getPrefix();
            String suffix = team.getSuffix();
            return (prefix != null && !prefix.isEmpty()) || (suffix != null && !suffix.isEmpty());
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** 是否因为已有团队前缀而跳过处理 */
    private boolean shouldSkipForTeam(Player player) {
        return config.namePrefixSkipTeam && hasTeamDecoration(player);
    }

    /**
     * 纯逻辑：计算前缀应插入的下标（负数表示格式里没有 {@code %1$s}）。
     */
    public static int chatInsertIndex(String format) {
        if (format == null) {
            return -1;
        }
        int index = format.indexOf("%1$s");
        if (index < 0) {
            return -1;
        }
        String head = format.substring(0, index);
        Matcher matcher = TRAILING_DECOR.matcher(head);
        int coreEnd = matcher.find() ? matcher.start() : head.length();
        if (coreEnd > 0 && OPENING_BRACKETS.indexOf(head.charAt(coreEnd - 1)) >= 0) {
            // 名字前面是左括号 -> 插到括号外面
            return coreEnd - 1;
        }
        // 没有括号就插在名字前面
        return index;
    }

    /**
     * 纯逻辑：把前缀插到合适位置；格式里没有 {@code %1$s} 或前缀为空时原样返回。
     */
    public static String injectChatPrefix(String format, String prefix) {
        if (format == null || prefix == null || prefix.isEmpty()) {
            return format;
        }
        int at = chatInsertIndex(format);
        if (at < 0) {
            return format;
        }
        return format.substring(0, at) + prefix + format.substring(at);
    }

    /**
     * 纯逻辑："只追加、不覆盖"的核心。
     * 先去掉我们上次加的前缀（避免重复叠加），再把新前缀拼到最前面，
     * 所以其他插件/原版已有的前缀都会原样保留。
     */
    public static String applyTabPrefix(String currentName, String lastPrefix, String newPrefix) {
        String base = currentName == null ? "" : currentName;
        if (lastPrefix != null && !lastPrefix.isEmpty()) {
            int index = base.indexOf(lastPrefix);
            if (index >= 0) {
                base = base.substring(0, index) + base.substring(index + lastPrefix.length());
            }
        }
        return (newPrefix == null ? "" : newPrefix) + base;
    }

    // =========================================================
    //  TAB 玩家列表
    // =========================================================

    public void updateAll() {
        if (!tabEnabled() || !plugin.isEnabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            update(player);
        }
    }

    /**
     * 刷新单个玩家的 TAB 列表名。
     * <p>只要当前的名字还不等于「我们想要的样子」就重算一次：
     * 先用上次加的前缀还原出基础名（其他插件/原版的内容会完整保留），再把新前缀拼到最前面。</p>
     * <p>若玩家已有原版团队前缀（且开启了 skip-team-prefix），则不加前缀，
     * 并把列表名恢复成原版（{@code null}），使原版前缀重新生效。</p>
     */
    public void update(Player player) {
        if (!tabEnabled() || player == null || !player.isOnline()) {
            return;
        }
        String desired = shouldSkipForTeam(player) ? "" : buildPrefix(config.namePrefixTabText, player, true);
        String last = appliedTabPrefix.get(player.getUniqueId());
        String lastValue = last == null ? "" : last;

        String current = player.getPlayerListName();
        if (current == null || current.isEmpty()) {
            current = player.getName();
        }

        String target = applyTabPrefix(current, lastValue, desired);
        if (target.equals(current)) {
            return; // 已经是目标样子, 不下发
        }

        try {
            // 目标就是原始名字时用 null 彻底恢复原版(原版团队前缀会重新生效)
            player.setPlayerListName(target.equals(player.getName()) ? null : target);
            appliedTabPrefix.put(player.getUniqueId(), desired);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "设置玩家列表名失败(可能名字过长或被其他插件占用): " + throwable.getMessage());
            appliedTabPrefix.put(player.getUniqueId(), desired);
        }
    }

    // =========================================================
    //  前缀文本
    // =========================================================

    /**
     * 把配置模板里占位符替换成实际内容。
     *
     * @param applyPapi 是否解析 PlaceholderAPI 占位符
     */
    public String buildPrefix(String template, Player player, boolean applyPapi) {
        if (template == null || template.isEmpty() || player == null) {
            return "";
        }
        AuthResult result = service.resolveForDisplay(player.getName());
        Map<String, String> values = PlaceholderValues.build(config, plugin.getMode(),
                plugin.getServer().getOnlineMode(), player.getName(), result, result == null);
        return Msg.format(player, template, values, applyPapi);
    }
}
