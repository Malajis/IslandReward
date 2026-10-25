package me.malajis.IslandReward;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI 扩展
 * 变量列表：
 * %islandreward_completion%              - 累计通关次数
 * %islandreward_threshold%               - 通关阈值
 * %islandreward_previous_level%          - 当前配置前周目的等级
 * %islandreward_previous_level_N%        - 指定第N周目的等级 (如 previous_level_1)
 * %islandreward_claimed%                 - 当前前周目是否已领取 (true/false)
 * %islandreward_claimed_N%               - 指定第N周目是否已领取 (如 claimed_1)
 * %islandreward_claimed_status%          - 当前前周目领取状态文字
 * %islandreward_claimed_status_N%        - 指定第N周目领取状态文字
 * %islandreward_completed%               - 当前配置前周目是否通关 (true/false)
 * %islandreward_completed_N%             - 指定第N周目是否通关 (如 completed_1)
 * %islandreward_completed_status%        - 当前配置前周目通关状态文字
 * %islandreward_completed_status_N%      - 指定第N周目通关状态文字
 * %islandreward_previous_week%           - 前周目名称
 * %islandreward_current_week%            - 当前周目名称
 * %islandreward_export_enabled%          - 奖励领取是否开启
 */
public class IslandRewardExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public IslandRewardExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "islandreward";
    }

    @Override
    public String getAuthor() {
        return "Malajis";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getName() == null) {
            return "";
        }

        String playerName = player.getName();
        String lower = params.toLowerCase();

        if (lower.equals("completion")) {
            return String.valueOf(plugin.getCompletionCount(playerName));
        }

        if (lower.equals("threshold")) {
            return String.valueOf(plugin.getCompletionThreshold());
        }

        if (lower.startsWith("previous_level")) {
            int weekNum = parseWeekSuffix(lower, "previous_level");
            int level = weekNum == 0
                    ? plugin.getPreviousLevel(playerName)
                    : plugin.getLevelForWeek(playerName, weekNum);
            return level > 0 ? String.valueOf(level) : "0";
        }

        if (lower.startsWith("completed_status")) {
            int weekNum = parseWeekSuffix(lower, "completed_status");
            boolean completed = weekNum == 0
                    ? plugin.getPreviousLevel(playerName) >= plugin.getCompletionThreshold()
                    : plugin.getLevelForWeek(playerName, weekNum) >= plugin.getCompletionThreshold();
            return completed
                    ? plugin.getMessage("status-completed")
                    : plugin.getMessage("status-not-completed");
        }

        if (lower.startsWith("completed")) {
            int weekNum = parseWeekSuffix(lower, "completed");
            boolean completed = weekNum == 0
                    ? plugin.getPreviousLevel(playerName) >= plugin.getCompletionThreshold()
                    : plugin.getLevelForWeek(playerName, weekNum) >= plugin.getCompletionThreshold();
            return String.valueOf(completed);
        }

        if (lower.startsWith("claimed_status")) {
            int weekNum = parseWeekSuffix(lower, "claimed_status");
            boolean claimed = weekNum == 0
                    ? plugin.isClaimed(playerName)
                    : plugin.isClaimed(playerName, weekNum);
            return claimed ? plugin.getMessage("status-claimed") : plugin.getMessage("status-unclaimed");
        }

        if (lower.startsWith("claimed")) {
            int weekNum = parseWeekSuffix(lower, "claimed");
            boolean claimed = weekNum == 0
                    ? plugin.isClaimed(playerName)
                    : plugin.isClaimed(playerName, weekNum);
            return String.valueOf(claimed);
        }

        if (lower.equals("previous_week")) {
            return plugin.getPreviousWeek();
        }

        if (lower.equals("current_week")) {
            return plugin.getCurrentWeek();
        }

        if (lower.equals("export_enabled")) {
            return String.valueOf(plugin.isExportEnabled());
        }

        return null;
    }

    private int parseWeekSuffix(String param, String prefix) {
        String suffix = param.substring(prefix.length());
        if (suffix.isEmpty()) return 0;
        if (suffix.startsWith("_")) {
            try {
                return Integer.parseInt(suffix.substring(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
