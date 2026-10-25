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

    private static final List<String> PLAYER_COMMANDS = Arrays.asList("claim", "myinfo", "info", "help");
    private static final List<String> ADMIN_COMMANDS = Arrays.asList("completion", "record", "export", "reload", "reimport");

    private final Main plugin;

    public CommandTabCompleter(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("islandreward.admin");

        if (args.length == 1) {
            for (String cmd : PLAYER_COMMANDS) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
            if (isAdmin) {
                for (String cmd : ADMIN_COMMANDS) {
                    if (cmd.startsWith(args[0].toLowerCase())) {
                        completions.add(cmd);
                    }
                }
            }
            return completions;
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            if (sub.equals("info")) {
                return completePlayerNames(args[1]);
            }
            if (!isAdmin) return completions;

            if (sub.equals("completion")) {
                for (String c : Arrays.asList("add", "clear")) {
                    if (c.startsWith(args[1].toLowerCase())) {
                        completions.add(c);
                    }
                }
            }
            if (sub.equals("record")) {
                for (String c : Arrays.asList("info", "list", "clear")) {
                    if (c.startsWith(args[1].toLowerCase())) {
                        completions.add(c);
                    }
                }
            }
            return completions;
        }

        if (!isAdmin) return completions;

        if (args.length == 3) {
            if (sub.equals("completion") && args[1].equalsIgnoreCase("add")) {
                return completePlayerNames(args[2]);
            }
            if (sub.equals("completion") && args[1].equalsIgnoreCase("clear")) {
                if ("all".startsWith(args[2].toLowerCase())) {
                    completions.add("all");
                }
                completions.addAll(completePlayerNames(args[2]));
                return completions;
            }
            if (sub.equals("record") && args[1].equalsIgnoreCase("clear")) {
                if ("all".startsWith(args[2].toLowerCase())) {
                    completions.add("all");
                }
                completions.addAll(completePlayerNames(args[2]));
                return completions;
            }
            if (sub.equals("record") && args[1].equalsIgnoreCase("info")) {
                return completeWeekNumbers(args[2]);
            }
            return completions;
        }

        if (args.length == 4) {
            if (sub.equals("completion") && args[1].equalsIgnoreCase("add")) {
                for (int i = 1; i <= 100; i++) {
                    String num = String.valueOf(i);
                    if (num.startsWith(args[3])) {
                        completions.add(num);
                    }
                }
            }
            if (sub.equals("record") && args[1].equalsIgnoreCase("clear")) {
                return completeWeekNumbers(args[3]);
            }
            return completions;
        }

        return completions;
    }

    private List<String> completeWeekNumbers(String input) {
        List<String> completions = new ArrayList<>();
        if (plugin == null) return completions;
        for (int week : plugin.getDetectedWeeks()) {
            String num = String.valueOf(week);
            if (num.startsWith(input)) {
                completions.add(num);
            }
        }
        return completions;
    }

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
