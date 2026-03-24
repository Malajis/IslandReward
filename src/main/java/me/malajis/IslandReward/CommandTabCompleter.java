package me.malajis.IslandReward;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 命令自动补全
 */
public class CommandTabCompleter implements TabCompleter {

    // 玩家通用命令（所有玩家可见）
    private static final List<String> PLAYER_COMMANDS = Arrays.asList("claim", "info", "mycompletion", "help");
    // 管理员命令（只有管理员可见）
    private static final List<String> ADMIN_COMMANDS = Arrays.asList("export", "reload", "check", "add", "reimport", "clear", "clearcompletion");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("islandreward.admin");

        // 第一级参数补全
        if (args.length == 1) {
            // 玩家命令
            for (String cmd : PLAYER_COMMANDS) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
            // 管理员命令（仅管理员可见）
            if (isAdmin) {
                for (String cmd : ADMIN_COMMANDS) {
                    if (cmd.startsWith(args[0].toLowerCase())) {
                        completions.add(cmd);
                    }
                }
            }
            return completions;
        }

        // 第二级参数补全（仅管理员）
        if (args.length == 2 && isAdmin) {
            String subCommand = args[0].toLowerCase();

            // check/add 命令：补全玩家名
            if (subCommand.equals("check") || subCommand.equals("add")) {
                return completePlayerNames(args[1]);
            }

            // clear 命令
            if (subCommand.equals("clear")) {
                if ("all".startsWith(args[1].toLowerCase())) {
                    completions.add("all");
                }
                completions.addAll(completePlayerNames(args[1]));
                return completions;
            }

            // clearcompletion 命令
            if (subCommand.equals("clearcompletion")) {
                if ("all".startsWith(args[1].toLowerCase())) {
                    completions.add("all");
                }
                completions.addAll(completePlayerNames(args[1]));
                return completions;
            }
        }

        // 第三级参数补全（add 命令的数量）
        if (args.length == 3 && isAdmin && args[0].equalsIgnoreCase("add")) {
            for (int i = 1; i <= 100; i++) {
                String num = String.valueOf(i);
                if (num.startsWith(args[2])) {
                    completions.add(num);
                }
            }
            return completions;
        }

        return completions;
    }

    /**
     * 补全在线玩家名
     */
    private List<String> completePlayerNames(String input) {
        List<String> completions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(input.toLowerCase())) {
                completions.add(player.getName());
            }
        }
        return completions;
    }
}