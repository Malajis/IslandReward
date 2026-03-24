package me.malajis.IslandReward;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI 扩展
 * 变量列表：
 * %islandreward_completion%        - 累计通关次数
 * %islandreward_threshold%         - 通关阈值
 * %islandreward_previous_level%    - 前周目等级
 * %islandreward_claimed%           - 是否已领取 (true/false)
 * %islandreward_claimed_status%    - 领取状态文字
 * %islandreward_completed%         - 是否通关 (true/false)
 * %islandreward_completed_status%  - 通关状态文字
 * %islandreward_previous_week%     - 前周目名称
 * %islandreward_current_week%      - 当前周目名称
 * %islandreward_export_enabled%    - 奖励领取是否开启
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
        return true;
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

        switch (params.toLowerCase()) {
            case "completion":
                return String.valueOf(plugin.getCompletionCount(playerName));

            case "threshold":
                return String.valueOf(plugin.getCompletionThreshold());

            case "previous_level":
                int level = plugin.getPreviousLevel(playerName);
                return level > 0 ? String.valueOf(level) : "0";

            case "claimed":
                return String.valueOf(plugin.isClaimed(playerName));

            case "claimed_status":
                return plugin.isClaimed(playerName) ? plugin.getMessage("status-claimed") : plugin.getMessage("status-unclaimed");

            case "completed":
                return String.valueOf(plugin.getPreviousLevel(playerName) >= plugin.getCompletionThreshold());

            case "completed_status":
                return plugin.getPreviousLevel(playerName) >= plugin.getCompletionThreshold()
                        ? plugin.getMessage("status-completed") : plugin.getMessage("status-not-completed");

            case "previous_week":
                return plugin.getPreviousWeek();

            case "current_week":
                return plugin.getCurrentWeek();

            case "export_enabled":
                return String.valueOf(plugin.isExportEnabled());

            default:
                return null;
        }
    }
}