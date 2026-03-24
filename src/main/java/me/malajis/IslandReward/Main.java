package me.malajis.IslandReward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * IslandReward 主类
 * 功能：岛屿周目奖励系统 + BentoBox 数据导出
 */
public class Main extends JavaPlugin implements Listener {

    // ============================================================
    // 奖励系统数据
    // ============================================================
    private Map<String, Integer> playerCompletionCount = new HashMap<>();   // 玩家累计通关次数
    private Map<String, Integer> previousData = new HashMap<>();            // 前周目等级数据
    private Set<String> claimedThisWeek = new HashSet<>();                  // 已领取奖励的玩家

    // ============================================================
    // 导出系统
    // ============================================================
    private boolean exportEnabled;                                          // 是否允许领取奖励
    private String exportDataFile;                                          // 导出数据文件路径

    // ============================================================
    // 周目配置
    // ============================================================
    private int completionThreshold;                                        // 通关等级阈值
    private String currentWeek;                                             // 当前周目名称
    private String previousWeek;                                            // 前周目名称
    private String timeFormat;                                              // 时间显示格式

    // ============================================================
    // 语言配置
    // ============================================================
    private Map<String, String> messages = new HashMap<>();

    // ============================================================
    // 奖励配置
    // ============================================================
    private Map<Integer, List<RewardItem>> levelRewards = new HashMap<>();   // 等级区间物品奖励
    private Map<Integer, List<String>> levelCommands = new HashMap<>();      // 等级区间命令奖励
    private List<RewardItem> completionRewards = new ArrayList<>();          // 通关物品奖励
    private List<String> completionCommands = new ArrayList<>();              // 通关命令奖励
    private RewardItem souvenirConfig;                                       // 纪念品配置

    // ============================================================
    // 插件生命周期
    // ============================================================

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        loadConfig();
        loadMessages();

        loadPlayerCompletionData();
        loadPreviousData();
        loadClaimedData();

        getServer().getPluginManager().registerEvents(this, this);

        // 注册 PlaceholderAPI 扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new IslandRewardExpansion(this).register();
            getLogger().info("已注册 PlaceholderAPI 扩展");
        }

        // 注册命令
        if (getCommand("islandreward") != null) {
            getCommand("islandreward").setExecutor(this);
            getCommand("islandreward").setTabCompleter(new CommandTabCompleter());
        }
        if (getCommand("ir") != null) {
            getCommand("ir").setExecutor(this);
            getCommand("ir").setTabCompleter(new CommandTabCompleter());
        }

        getLogger().info("=== IslandReward 插件已启用 ===");
        getLogger().info("已加载 " + playerCompletionCount.size() + " 名玩家的通关次数数据");
        getLogger().info("已加载 " + previousData.size() + " 条" + previousWeek + "数据");
        getLogger().info("已领取玩家: " + claimedThisWeek.size() + " 人");
        getLogger().info("通关阈值: " + completionThreshold + " 级");
        getLogger().info("当前周目: " + currentWeek);
        getLogger().info("前周目: " + previousWeek);
        getLogger().info("奖励领取: " + (exportEnabled ? "已开启" : "已关闭"));
    }

    @Override
    public void onDisable() {
        saveAllData();
        getLogger().info("IslandReward 插件已卸载");
    }

    // ============================================================
    // 配置加载
    // ============================================================

    private void loadConfig() {
        reloadConfig();

        // 导出配置
        exportEnabled = getConfig().getBoolean("export.enabled", false);
        exportDataFile = getConfig().getString("export.data-file", "island_data.yml");

        // 周目配置
        completionThreshold = getConfig().getInt("completion-threshold", 10000);
        currentWeek = getConfig().getString("current-week", "第二周目");
        previousWeek = getConfig().getString("previous-week", "第一周目");
        timeFormat = getConfig().getString("time-format", "yyyy-MM-dd HH:mm:ss");

        // 等级奖励物品
        levelRewards.clear();
        ConfigurationSection levelSection = getConfig().getConfigurationSection("rewards.level-rewards");
        if (levelSection != null) {
            for (String key : levelSection.getKeys(false)) {
                try {
                    int minLevel = Integer.parseInt(key);
                    List<RewardItem> items = new ArrayList<>();
                    List<Map<?, ?>> itemList = levelSection.getMapList(key + ".items");
                    for (Map<?, ?> itemMap : itemList) {
                        String material = (String) itemMap.get("material");
                        int amount = (int) itemMap.get("amount");
                        items.add(new RewardItem(material, amount));
                    }
                    levelRewards.put(minLevel, items);
                } catch (NumberFormatException e) {
                    getLogger().warning("无效的等级区间配置: " + key);
                }
            }
        }

        // 等级奖励命令
        levelCommands.clear();
        if (levelSection != null) {
            for (String key : levelSection.getKeys(false)) {
                try {
                    int minLevel = Integer.parseInt(key);
                    List<String> commands = levelSection.getStringList(key + ".commands");
                    if (!commands.isEmpty()) {
                        levelCommands.put(minLevel, commands);
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }

        // 通关奖励物品
        completionRewards.clear();
        List<Map<?, ?>> completionList = getConfig().getMapList("rewards.completion.items");
        for (Map<?, ?> itemMap : completionList) {
            String material = (String) itemMap.get("material");
            int amount = (int) itemMap.get("amount");
            completionRewards.add(new RewardItem(material, amount));
        }

        // 通关奖励命令
        completionCommands.clear();
        completionCommands = getConfig().getStringList("rewards.completion.commands");

        // 纪念品配置
        ConfigurationSection souvenirSection = getConfig().getConfigurationSection("rewards.souvenir");
        if (souvenirSection != null) {
            String material = souvenirSection.getString("material", "NAME_TAG");
            String name = souvenirSection.getString("name", "§6§l⭐ {player} 的 {previous_week} 纪念品 ⭐");
            List<String> lore = souvenirSection.getStringList("lore");
            souvenirConfig = new RewardItem(material, 1, name, lore);
        } else {
            souvenirConfig = new RewardItem("NAME_TAG", 1, "§6§l⭐ {player} 的 {previous_week} 纪念品 ⭐", null);
        }

        getLogger().info("配置加载完成");
    }

    private void loadMessages() {
        messages.clear();
        ConfigurationSection msgSection = getConfig().getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(true)) {
                messages.put(key, msgSection.getString(key));
            }
        }
    }

    /**
     * 获取带前缀的消息（用于单独提示）
     */
    public String getMessage(String key) {
        String prefix = getConfig().getString("messages.prefix", "§8[§b周目奖励§8] ");
        String message = messages.getOrDefault(key, "§c未知消息: " + key);
        return prefix + message;
    }

    /**
     * 获取不带前缀的消息（用于框框、统计等）
     */
    public String getRawMessage(String key) {
        return messages.getOrDefault(key, "§c未知消息: " + key);
    }

    // ============================================================
    // 数据加载与保存
    // ============================================================

    private void loadPlayerCompletionData() {
        File file = new File(getDataFolder(), "completion_data.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String playerName : config.getKeys(false)) {
            int count = config.getInt(playerName + ".completion_count");
            playerCompletionCount.put(playerName, count);
        }
    }

    private void loadPreviousData() {
        File file = new File(getDataFolder(), exportDataFile);
        if (!file.exists()) {
            getLogger().warning("未找到" + previousWeek + "数据文件 " + exportDataFile + "！");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String playerName : config.getKeys(false)) {
            int level = config.getInt(playerName + ".level");
            previousData.put(playerName, level);
        }

        getLogger().info("从 " + exportDataFile + " 加载了 " + previousData.size() + " 条" + previousWeek + "数据");
    }

    private void loadClaimedData() {
        File file = new File(getDataFolder(), "claimed_this_week.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> claimedList = config.getStringList("claimed");
        claimedThisWeek.addAll(claimedList);
    }

    private void saveAllData() {
        saveCompletionData();
        saveClaimedData();
    }

    private void saveCompletionData() {
        File file = new File(getDataFolder(), "completion_data.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Integer> entry : playerCompletionCount.entrySet()) {
            config.set(entry.getKey() + ".completion_count", entry.getValue());
        }

        try {
            config.save(file);
        } catch (Exception e) {
            getLogger().severe("保存通关次数数据失败: " + e.getMessage());
        }
    }

    private void saveClaimedData() {
        File file = new File(getDataFolder(), "claimed_this_week.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("claimed", new ArrayList<>(claimedThisWeek));

        try {
            config.save(file);
        } catch (Exception e) {
            getLogger().severe("保存已领取记录失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 导出功能（从 BentoBox 读取数据）
    // ============================================================

    /**
     * 导出玩家等级数据到文件
     */
    public void exportData() {
        try {
            getLogger().info("开始导出岛屿数据...");

            Map<String, String> islandToOwner = loadIslandOwners();
            getLogger().info("加载了 " + islandToOwner.size() + " 个岛屿所有者映射");

            Map<String, String> uuidToName = loadPlayerNames();
            getLogger().info("加载了 " + uuidToName.size() + " 个玩家名映射");

            Map<String, Integer> islandLevels = getIslandLevelsFromJsonFiles();
            getLogger().info("加载了 " + islandLevels.size() + " 个岛屿等级");

            Map<String, Integer> playerLevels = new HashMap<>();
            int matched = 0, noOwner = 0, noPlayerName = 0, zeroLevel = 0;

            for (Map.Entry<String, Integer> entry : islandLevels.entrySet()) {
                String islandUuid = entry.getKey();
                int level = entry.getValue();

                if (level <= 0) {
                    zeroLevel++;
                    continue;
                }

                String ownerUuid = islandToOwner.get(islandUuid);
                if (ownerUuid == null) {
                    noOwner++;
                    continue;
                }

                String playerName = uuidToName.get(ownerUuid);
                if (playerName == null) {
                    noPlayerName++;
                    continue;
                }

                playerLevels.put(playerName, level);
                matched++;
            }

            getLogger().info("合并完成:");
            getLogger().info("  - 成功匹配: " + matched + " 条");
            getLogger().info("  - 跳过0级: " + zeroLevel + " 条");
            getLogger().info("  - 无岛主信息: " + noOwner + " 条");
            getLogger().info("  - 无玩家名: " + noPlayerName + " 条");

            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<String, Integer> entry : playerLevels.entrySet()) {
                config.set(entry.getKey() + ".level", entry.getValue());
            }

            saveExportedData(config, playerLevels.size());

        } catch (Exception e) {
            getLogger().severe("导出失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, String> loadIslandOwners() {
        Map<String, String> islandToOwner = new HashMap<>();
        File islandFolder = new File("plugins/BentoBox/database/Island");

        if (!islandFolder.exists()) {
            getLogger().warning("Island 文件夹不存在: " + islandFolder.getAbsolutePath());
            return islandToOwner;
        }

        File[] islandFiles = islandFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (islandFiles == null || islandFiles.length == 0) {
            getLogger().warning("Island 文件夹中没有找到任何 JSON 文件");
            return islandToOwner;
        }

        Gson gson = new Gson();
        for (File islandFile : islandFiles) {
            try {
                String fileName = islandFile.getName();
                String islandUuid = fileName.replace("BSkyBlock", "").replace(".json", "");

                try (FileReader reader = new FileReader(islandFile)) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    if (json.has("owner") && !json.get("owner").isJsonNull()) {
                        String ownerUuid = json.get("owner").getAsString();
                        islandToOwner.put(islandUuid, ownerUuid);
                    }
                }
            } catch (Exception e) {
                getLogger().warning("读取岛屿文件失败: " + islandFile.getName() + " - " + e.getMessage());
            }
        }

        return islandToOwner;
    }

    private Map<String, String> loadPlayerNames() {
        Map<String, String> uuidToName = new HashMap<>();
        File playersFolder = new File("plugins/BentoBox/database/Players");

        if (!playersFolder.exists()) {
            getLogger().warning("Players 文件夹不存在: " + playersFolder.getAbsolutePath());
            return uuidToName;
        }

        File[] playerFiles = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null || playerFiles.length == 0) {
            getLogger().warning("Players 文件夹中没有找到任何 JSON 文件");
            return uuidToName;
        }

        Gson gson = new Gson();
        for (File playerFile : playerFiles) {
            try {
                try (FileReader reader = new FileReader(playerFile)) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    String uniqueId = json.get("uniqueId").getAsString();
                    String playerName = json.get("playerName").getAsString();
                    uuidToName.put(uniqueId, playerName);
                }
            } catch (Exception e) {
                getLogger().warning("读取玩家文件失败: " + playerFile.getName() + " - " + e.getMessage());
            }
        }

        return uuidToName;
    }

    private Map<String, Integer> getIslandLevelsFromJsonFiles() {
        Map<String, Integer> islandLevels = new HashMap<>();
        File levelFolder = new File("plugins/BentoBox/database/IslandLevels");

        if (!levelFolder.exists()) {
            getLogger().severe("IslandLevels 文件夹不存在");
            return islandLevels;
        }

        File[] files = levelFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            getLogger().severe("没有找到任何 JSON 文件");
            return islandLevels;
        }

        Gson gson = new Gson();
        for (File file : files) {
            try {
                String fileName = file.getName();
                String islandUuid = fileName.replace("BSkyBlock", "").replace(".json", "");

                try (FileReader reader = new FileReader(file)) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    int level = json.get("level").getAsInt();
                    islandLevels.put(islandUuid, level);
                }
            } catch (Exception e) {
                getLogger().warning("读取等级文件失败: " + file.getName() + " - " + e.getMessage());
            }
        }

        return islandLevels;
    }

    private void saveExportedData(YamlConfiguration config, int count) {
        File file = new File(getDataFolder(), exportDataFile);
        try {
            config.save(file);
            getLogger().info("========================================");
            getLogger().info("导出完成！共导出: " + count + " 条玩家等级数据");
            getLogger().info("文件位置: " + file.getAbsolutePath());
            getLogger().info("========================================");
        } catch (Exception e) {
            getLogger().severe("保存文件失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 玩家事件
    // ============================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        if (exportEnabled && !claimedThisWeek.contains(playerName) && previousData.containsKey(playerName)) {
            int oldLevel = previousData.get(playerName);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    sendReminder(player, oldLevel);
                }
            }, 40L);
        }

        int completionCount = playerCompletionCount.getOrDefault(playerName, 0);
        if (completionCount > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    player.sendMessage(getMessage("completion-display").replace("{count}", String.valueOf(completionCount)));
                }
            }, 60L);
        }
    }

    private void sendReminder(Player player, int oldLevel) {
        player.sendMessage(getRawMessage("reminder-header"));
        player.sendMessage(getRawMessage("reminder-message")
                .replace("{previous_week}", previousWeek)
                .replace("{level}", String.valueOf(oldLevel)));
        if (oldLevel >= completionThreshold) {
            player.sendMessage(getRawMessage("reminder-completed"));
        }
        player.sendMessage(getRawMessage("reminder-commands"));
        player.sendMessage(getRawMessage("reminder-footer"));
    }

    // ============================================================
    // 玩家命令
    // ============================================================

    private void claimReward(Player player) {
        if (!exportEnabled) {
            player.sendMessage(getMessage("reward-disabled"));  // 单独提示，有前缀
            return;
        }

        String playerName = player.getName();

        if (claimedThisWeek.contains(playerName)) {
            player.sendMessage(getMessage("already-claimed").replace("{previous_week}", previousWeek));  // 单独提示，有前缀
            return;
        }

        if (!previousData.containsKey(playerName)) {
            player.sendMessage(getMessage("no-data").replace("{previous_week}", previousWeek));  // 单独提示，有前缀
            return;
        }

        int oldLevel = previousData.get(playerName);
        int newCompletions = (oldLevel >= completionThreshold) ? 1 : 0;

        giveRewards(player, oldLevel, newCompletions);

        int currentCount = playerCompletionCount.getOrDefault(playerName, 0);
        playerCompletionCount.put(playerName, currentCount + newCompletions);
        saveCompletionData();

        claimedThisWeek.add(playerName);
        saveClaimedData();

        // 框框 - 无前缀
        player.sendMessage(getRawMessage("reward-header"));
        // 框框内的内容 - 无前缀
        player.sendMessage(getRawMessage("reward-success")
                .replace("{previous_week}", previousWeek)
                .replace("{level}", String.valueOf(oldLevel)));

        if (newCompletions > 0) {
            player.sendMessage(getRawMessage("reward-completed").replace("{previous_week}", previousWeek));
            player.sendMessage(getRawMessage("completion-total").replace("{count}", String.valueOf(currentCount + newCompletions)));
        } else {
            player.sendMessage(getRawMessage("reward-not-completed").replace("{threshold}", String.valueOf(completionThreshold)));
        }
        // 框框 - 无前缀
        player.sendMessage(getRawMessage("reward-footer"));

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void showInfo(Player player) {
        String playerName = player.getName();

        if (!previousData.containsKey(playerName)) {
            player.sendMessage(getMessage("no-data").replace("{previous_week}", previousWeek));
            return;
        }

        int oldLevel = previousData.get(playerName);
        boolean completed = oldLevel >= completionThreshold;
        boolean claimed = claimedThisWeek.contains(playerName);

        player.sendMessage(getRawMessage("info-header").replace("{previous_week}", previousWeek));
        player.sendMessage(getRawMessage("info-level")
                .replace("{previous_week}", previousWeek)
                .replace("{level}", String.valueOf(oldLevel)));
        player.sendMessage(getRawMessage("info-completed") +
                (completed ? getRawMessage("info-completed-yes").replace("{threshold}", String.valueOf(completionThreshold))
                        : getRawMessage("info-completed-no")));
        player.sendMessage(getRawMessage("info-completion-gain") +
                (completed ? getRawMessage("info-gain-yes") : getRawMessage("info-gain-no")));
        player.sendMessage(getRawMessage("info-claim-status") +
                (claimed ? getRawMessage("info-claimed-yes") : getRawMessage("info-claimed-no")));
        player.sendMessage(getRawMessage("info-footer"));

        if (!claimed && exportEnabled) {
            player.sendMessage(getMessage("info-claim-command"));
        }
    }

    private void showMyCompletion(Player player) {
        int count = playerCompletionCount.getOrDefault(player.getName(), 0);
        player.sendMessage(getRawMessage("mycompletion-header"));
        player.sendMessage(getRawMessage("mycompletion-count").replace("{count}", String.valueOf(count)));
        player.sendMessage(getRawMessage("mycompletion-threshold").replace("{threshold}", String.valueOf(completionThreshold)));
        player.sendMessage(getRawMessage("mycompletion-footer"));
    }

    // ============================================================
    // 奖励发放
    // ============================================================

    private void giveRewards(Player player, int oldLevel, int completions) {
        String playerName = player.getName();
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat);
        String date = sdf.format(new Date());

        // 纪念品
        ItemStack souvenir = createSouvenir(playerName, oldLevel, completions, date);
        addItemToInventory(player, souvenir);

        // 等级奖励（只发放最高档位）
        List<Integer> sortedLevels = new ArrayList<>(levelRewards.keySet());
        sortedLevels.sort(Collections.reverseOrder());

        for (int minLevel : sortedLevels) {
            if (oldLevel >= minLevel) {
                // 物品奖励
                List<RewardItem> items = levelRewards.get(minLevel);
                if (items != null) {
                    for (RewardItem reward : items) {
                        addItemToInventory(player, reward.toItemStack());
                    }
                }
                // 命令奖励
                List<String> commands = levelCommands.get(minLevel);
                if (commands != null) {
                    for (String command : commands) {
                        String cmd = command.replace("{player}", playerName)
                                .replace("{level}", String.valueOf(oldLevel))
                                .replace("{completions}", String.valueOf(completions))
                                .replace("{previous_week}", previousWeek)
                                .replace("{current_week}", currentWeek);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                }
                break;
            }
        }

        // 通关奖励
        if (completions > 0) {
            for (RewardItem reward : completionRewards) {
                addItemToInventory(player, reward.toItemStack());
            }
            for (String command : completionCommands) {
                String cmd = command.replace("{player}", playerName)
                        .replace("{level}", String.valueOf(oldLevel))
                        .replace("{completions}", String.valueOf(completions))
                        .replace("{previous_week}", previousWeek)
                        .replace("{current_week}", currentWeek);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    private ItemStack createSouvenir(String playerName, int level, int completions, String date) {
        ItemStack item = souvenirConfig.toItemStack();
        ItemMeta meta = item.getItemMeta();

        // 替换 Lore 中的变量
        List<String> lore = souvenirConfig.getLore();
        if (lore != null) {
            List<String> newLore = new ArrayList<>();
            for (String line : lore) {
                line = line.replace("{player}", playerName)
                        .replace("{level}", String.valueOf(level))
                        .replace("{completions}", String.valueOf(completions))
                        .replace("{date}", date)
                        .replace("{previous_week}", previousWeek)
                        .replace("{current_week}", currentWeek);
                newLore.add(line);
            }
            meta.setLore(newLore);
        }

        // 替换名称中的变量
        String displayName = souvenirConfig.getName();
        if (displayName != null) {
            displayName = displayName.replace("{player}", playerName)
                    .replace("{level}", String.valueOf(level))
                    .replace("{completions}", String.valueOf(completions))
                    .replace("{date}", date)
                    .replace("{previous_week}", previousWeek)
                    .replace("{current_week}", currentWeek);
            meta.setDisplayName(displayName);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void addItemToInventory(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(getMessage("inventory-full"));
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }
    }

    // ============================================================
    // 管理员命令
    // ============================================================

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label,
                             String[] args) {

        if (command.getName().equalsIgnoreCase("islandreward") || command.getName().equalsIgnoreCase("ir")) {

            // 帮助命令
            if (args.length == 0 || (args.length >= 1 && args[0].equalsIgnoreCase("help"))) {
                sendHelp(sender);
                return true;
            }

            // 玩家命令
            if (sender instanceof Player) {
                Player player = (Player) sender;

                switch (args[0].toLowerCase()) {
                    case "claim":
                        claimReward(player);
                        return true;
                    case "info":
                        showInfo(player);
                        return true;
                    case "mycompletion":
                        showMyCompletion(player);
                        return true;
                }
            }

            // 管理员权限检查
            if (!sender.hasPermission("islandreward.admin")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }

            // 管理员命令
            switch (args[0].toLowerCase()) {
                case "export":
                    exportData();
                    sender.sendMessage(getMessage("export-started"));
                    return true;

                case "reload":
                    reloadPlugin(sender);
                    break;

                case "check":
                    if (args.length >= 2) {
                        checkPlayer(sender, args[1]);
                    } else {
                        sender.sendMessage(getMessage("usage-check"));
                    }
                    break;

                case "add":
                    if (args.length >= 3) {
                        try {
                            addCompletion(sender, args[1], Integer.parseInt(args[2]));
                        } catch (NumberFormatException e) {
                            sender.sendMessage(getMessage("not-number"));
                        }
                    } else {
                        sender.sendMessage(getMessage("usage-add"));
                    }
                    break;

                case "reimport":
                    reimportData(sender);
                    break;

                case "clear":
                    clearClaimedData(sender, args);
                    break;

                case "clearcompletion":
                case "clearc":
                    clearCompletionData(sender, args);
                    break;

                default:
                    sendHelp(sender);
                    break;
            }
            return true;
        }

        return false;
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("islandreward.admin");

        sender.sendMessage(getRawMessage("help-header"));
        sender.sendMessage(getRawMessage("help-player-title"));
        sender.sendMessage(getRawMessage("help-claim").replace("{previous_week}", previousWeek));
        sender.sendMessage(getRawMessage("help-info").replace("{previous_week}", previousWeek));
        sender.sendMessage(getRawMessage("help-mycompletion"));

        if (isAdmin) {
            sender.sendMessage("");
            sender.sendMessage(getRawMessage("help-admin-title"));
            sender.sendMessage(getRawMessage("help-export"));
            sender.sendMessage(getRawMessage("help-reload"));
            sender.sendMessage(getRawMessage("help-check"));
            sender.sendMessage(getRawMessage("help-add"));
            sender.sendMessage(getRawMessage("help-reimport").replace("{previous_week}", previousWeek));
            sender.sendMessage(getRawMessage("help-clear"));
            sender.sendMessage(getRawMessage("help-clearcompletion"));
        }

        sender.sendMessage("");
        sender.sendMessage(getRawMessage("help-help"));
        sender.sendMessage(getRawMessage("help-footer"));

        if (!isAdmin) {
            sender.sendMessage(getRawMessage("help-permission-note"));
        }
    }

    private void reloadPlugin(CommandSender sender) {
        saveAllData();
        playerCompletionCount.clear();
        previousData.clear();
        claimedThisWeek.clear();

        loadConfig();
        loadMessages();
        loadPlayerCompletionData();
        loadPreviousData();
        loadClaimedData();

        sender.sendMessage(getRawMessage("reload-success"));
        sender.sendMessage(getRawMessage("reload-completion").replace("{count}", String.valueOf(playerCompletionCount.size())));
        sender.sendMessage(getRawMessage("reload-data").replace("{previous_week}", previousWeek).replace("{count}", String.valueOf(previousData.size())));
        sender.sendMessage(getRawMessage("reload-claimed").replace("{count}", String.valueOf(claimedThisWeek.size())));
        sender.sendMessage(getRawMessage("reload-status").replace("{status}", exportEnabled ? getRawMessage("status-enabled") : getRawMessage("status-disabled")));
    }

    private void checkPlayer(CommandSender sender, String playerName) {
        int count = playerCompletionCount.getOrDefault(playerName, 0);
        int oldLevel = previousData.getOrDefault(playerName, -1);

        sender.sendMessage(getRawMessage("check-header").replace("{player}", playerName));
        sender.sendMessage(getRawMessage("check-completion").replace("{count}", String.valueOf(count)));

        if (oldLevel != -1) {
            String levelMsg = getRawMessage("check-level")
                    .replace("{previous_week}", previousWeek)
                    .replace("{level}", String.valueOf(oldLevel));
            sender.sendMessage(levelMsg);

            boolean completed = oldLevel >= completionThreshold;
            String completedMsg = getRawMessage("check-completed").replace("{previous_week}", previousWeek);
            sender.sendMessage(completedMsg + (completed ? getRawMessage("check-yes") : getRawMessage("check-no")));
            sender.sendMessage(getRawMessage("check-claimed") + (claimedThisWeek.contains(playerName) ? getRawMessage("check-claimed-yes") : getRawMessage("check-claimed-no")));
        } else {
            String noDataMsg = getRawMessage("check-no-data").replace("{previous_week}", previousWeek);
            sender.sendMessage(noDataMsg);
        }
    }

    private void addCompletion(CommandSender sender, String playerName, int amount) {
        int current = playerCompletionCount.getOrDefault(playerName, 0);
        playerCompletionCount.put(playerName, current + amount);
        saveCompletionData();
        sender.sendMessage(getMessage("add-success").replace("{player}", playerName).replace("{amount}", String.valueOf(amount)));
        sender.sendMessage(getMessage("add-current").replace("{count}", String.valueOf(current + amount)));
    }

    private void reimportData(CommandSender sender) {
        previousData.clear();
        loadPreviousData();
        sender.sendMessage(getMessage("reimport-success").replace("{previous_week}", previousWeek).replace("{count}", String.valueOf(previousData.size())));
        sender.sendMessage(getMessage("reimport-hint"));
    }

    private void clearClaimedData(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage(getMessage("clear-claimed-usage"));
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            int count = claimedThisWeek.size();
            claimedThisWeek.clear();
            saveClaimedData();
            sender.sendMessage(getMessage("clear-claimed-all").replace("{count}", String.valueOf(count)));
            sender.sendMessage(getMessage("clear-claimed-hint").replace("{previous_week}", previousWeek));
            return;
        }

        String playerName = args[1];
        if (claimedThisWeek.contains(playerName)) {
            claimedThisWeek.remove(playerName);
            saveClaimedData();
            sender.sendMessage(getMessage("clear-claimed-player").replace("{player}", playerName));
            sender.sendMessage(getMessage("clear-claimed-player-hint").replace("{previous_week}", previousWeek));

            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                target.sendMessage(getRawMessage("clear-claimed-notify-header"));
                target.sendMessage(getRawMessage("clear-claimed-notify-message"));
                if (previousData.containsKey(playerName)) {
                    int oldLevel = previousData.get(playerName);
                    target.sendMessage(getRawMessage("clear-claimed-notify-level").replace("{previous_week}", previousWeek).replace("{level}", String.valueOf(oldLevel)));
                }
                target.sendMessage(getRawMessage("clear-claimed-notify-command"));
                target.sendMessage(getRawMessage("clear-claimed-notify-footer"));
            }
        } else {
            sender.sendMessage(getMessage("clear-claimed-not-found").replace("{player}", playerName));
        }
    }

    private void clearCompletionData(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage(getMessage("clear-completion-usage"));
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            int count = playerCompletionCount.size();
            playerCompletionCount.clear();
            saveCompletionData();
            sender.sendMessage(getMessage("clear-completion-all").replace("{count}", String.valueOf(count)));
            return;
        }

        String playerName = args[1];
        if (playerCompletionCount.containsKey(playerName)) {
            int oldCount = playerCompletionCount.get(playerName);
            playerCompletionCount.remove(playerName);
            saveCompletionData();
            sender.sendMessage(getMessage("clear-completion-player").replace("{player}", playerName));
            sender.sendMessage(getMessage("clear-completion-old").replace("{count}", String.valueOf(oldCount)));

            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                target.sendMessage(getRawMessage("clear-completion-notify-header"));
                target.sendMessage(getRawMessage("clear-completion-notify-message"));
                target.sendMessage(getRawMessage("clear-completion-notify-old").replace("{count}", String.valueOf(oldCount)));
                target.sendMessage(getRawMessage("clear-completion-notify-footer"));
            }
        } else {
            sender.sendMessage(getMessage("clear-completion-not-found").replace("{player}", playerName));
        }
    }

    // ============================================================
    // PlaceholderAPI 公共方法
    // ============================================================

    public int getCompletionCount(String playerName) { return playerCompletionCount.getOrDefault(playerName, 0); }
    public int getCompletionThreshold() { return completionThreshold; }
    public int getPreviousLevel(String playerName) { return previousData.getOrDefault(playerName, -1); }
    public boolean isClaimed(String playerName) { return claimedThisWeek.contains(playerName); }
    public String getPreviousWeek() { return previousWeek; }
    public String getCurrentWeek() { return currentWeek; }
    public boolean isExportEnabled() { return exportEnabled; }

    // ============================================================
    // 内部类
    // ============================================================

    /**
     * 奖励物品配置类
     */
    private static class RewardItem {
        private final String material;
        private final int amount;
        private final String name;
        private final List<String> lore;

        public RewardItem(String material, int amount) {
            this(material, amount, null, null);
        }

        public RewardItem(String material, int amount, String name, List<String> lore) {
            this.material = material;
            this.amount = amount;
            this.name = name;
            this.lore = lore;
        }

        public ItemStack toItemStack() {
            try {
                Material mat = Material.valueOf(material.toUpperCase());
                ItemStack item = new ItemStack(mat, amount);

                if (name != null || lore != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (name != null) meta.setDisplayName(name);
                    if (lore != null) meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                return item;
            } catch (IllegalArgumentException e) {
                return new ItemStack(Material.AIR);
            }
        }

        public String getName() { return name; }
        public List<String> getLore() { return lore; }
    }
}