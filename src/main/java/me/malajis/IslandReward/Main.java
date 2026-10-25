package me.malajis.IslandReward;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    private Map<String, Integer> previousData = new HashMap<>();            // 当前配置的前周目等级数据

    // 领取记录按周目归档： 周目编号 -> (玩家名 -> 领取记录)
    private Map<Integer, Map<String, ClaimRecord>> claimsByWeek = new HashMap<>();
    // 每个周目已经发放过通关次数的玩家（独立于领取记录，防止清记录后重复累加通关次数）
    private Map<Integer, Set<String>> completionAwardedByWeek = new HashMap<>();
    private boolean claimsLoaded = false;                                    // 领取记录是否成功加载

    // ============================================================
    // 多周目数据
    // ============================================================
    private Map<Integer, Map<String, Integer>> allWeekData = new HashMap<>(); // 周目编号 -> 玩家等级数据
    private List<Integer> detectedWeeks = new ArrayList<>();                  // 检测到的所有周目编号
    private int previousWeekIndex = 1;                                        // 当前读取的上个周目文件编号

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
        updateConfig();
        loadConfig();
        loadMessages();

        loadPlayerCompletionData();
        loadPreviousData();
        loadClaimedData();

        getServer().getPluginManager().registerEvents(this, this);

        // 注册 PlaceholderAPI 扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new IslandRewardExpansion(this).register();
        }

        // 注册命令
        if (getCommand("islandreward") != null) {
            getCommand("islandreward").setExecutor(this);
            getCommand("islandreward").setTabCompleter(new CommandTabCompleter(this));
        }
        if (getCommand("ir") != null) {
            getCommand("ir").setExecutor(this);
            getCommand("ir").setTabCompleter(new CommandTabCompleter(this));
        }

        getLogger().info("=== IslandReward 插件已启用 ===");
        getLogger().info("已加载 " + playerCompletionCount.size() + " 名玩家的通关次数数据");
        getLogger().info("检测到 " + detectedWeeks.size() + " 个周目数据: " + detectedWeeks);
        getLogger().info("当前前周目数据编号: " + previousWeekIndex + "，共 " + previousData.size() + " 条");
        getLogger().info("当前周目已领取玩家: " + getClaimedCount(previousWeekIndex) + " 人");
        getLogger().info("历史领取记录周目: " + claimsByWeek.keySet());
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

    private void updateConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration defaultConfig = new YamlConfiguration();

        try {
            java.io.InputStream defaultStream = getResource("config.yml");
            if (defaultStream == null) return;
            defaultConfig.load(new java.io.InputStreamReader(defaultStream));
        } catch (Exception e) {
            getLogger().warning("无法读取默认配置: " + e.getMessage());
            return;
        }

        List<String> addedKeys = new ArrayList<>();
        mergeMissingKeys(defaultConfig, currentConfig, "", addedKeys);

        if (!addedKeys.isEmpty()) {
            try {
                currentConfig.save(configFile);
                getLogger().info("已自动补全 " + addedKeys.size() + " 个缺失的配置项: " + addedKeys);
            } catch (Exception e) {
                getLogger().warning("保存补全后的配置失败: " + e.getMessage());
            }
        }
    }

    private void mergeMissingKeys(ConfigurationSection defaultSection, ConfigurationSection targetSection, String parentPath, List<String> addedKeys) {
        for (String key : defaultSection.getKeys(false)) {
            String fullKey = parentPath.isEmpty() ? key : parentPath + "." + key;

            if (defaultSection.isConfigurationSection(key)) {
                ConfigurationSection subDefault = defaultSection.getConfigurationSection(key);
                if (!targetSection.isConfigurationSection(key)) {
                    targetSection.createSection(key);
                    addedKeys.add(fullKey + " (新增节点)");
                }
                ConfigurationSection subTarget = targetSection.getConfigurationSection(key);
                mergeMissingKeys(subDefault, subTarget, fullKey, addedKeys);
            } else {
                if (!targetSection.contains(key)) {
                    Object defaultValue = defaultSection.get(key);
                    targetSection.set(key, defaultValue);
                    addedKeys.add(fullKey);
                }
            }
        }
    }

    private void loadConfig() {
        reloadConfig();

        // 导出配置
        exportEnabled = getConfig().getBoolean("export.enabled", false);
        exportDataFile = getConfig().getString("export.data-file", "island_data.yml");

        // 周目配置
        completionThreshold = getConfig().getInt("completion-threshold", 10000);
        currentWeek = getConfig().getString("current-week", "第二周目");
        previousWeek = getConfig().getString("previous-week", "第一周目");
        previousWeekIndex = getConfig().getInt("previous-week-data", 1);
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
        allWeekData.clear();
        detectedWeeks.clear();
        previousData.clear();

        File dataFolder = getDataFolder();
        File[] dataFiles = dataFolder.listFiles((dir, name) -> name.startsWith("island_data_") && name.endsWith(".yml"));

        if (dataFiles == null || dataFiles.length == 0) {
            getLogger().warning("未找到任何 island_data_*.yml 周目数据文件！");
            return;
        }

        for (File file : dataFiles) {
            String fileName = file.getName();
            String numStr = fileName.replace("island_data_", "").replace(".yml", "");

            try {
                int weekNum = Integer.parseInt(numStr);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                Map<String, Integer> weekData = new HashMap<>();

                for (String playerName : config.getKeys(false)) {
                    int level = config.getInt(playerName + ".level");
                    weekData.put(playerName, level);
                }

                allWeekData.put(weekNum, weekData);
                detectedWeeks.add(weekNum);
                getLogger().info("从 " + fileName + " 加载了 " + weekData.size() + " 条数据（第" + weekNum + "周目）");
            } catch (NumberFormatException e) {
                getLogger().warning("无法解析周目编号: " + fileName + "，已跳过");
            }
        }

        Collections.sort(detectedWeeks);

        if (allWeekData.containsKey(previousWeekIndex)) {
            previousData = allWeekData.get(previousWeekIndex);
        } else {
            getLogger().warning("未找到第" + previousWeekIndex + "周目数据文件 island_data_" + previousWeekIndex + ".yml！");
        }
    }

    // ============================================================
    // 领取记录（按周目归档）
    // ============================================================

    /**
     * 读取某一周目的领取记录（不存在时创建空表）
     */
    private Map<String, ClaimRecord> claimsOf(int week) {
        return claimsByWeek.computeIfAbsent(week, k -> new LinkedHashMap<>());
    }

    /**
     * 读取某周目“已发放通关次数”的玩家集合（不存在时创建空表）
     */
    private Set<String> awardedOf(int week) {
        return completionAwardedByWeek.computeIfAbsent(week, k -> new LinkedHashSet<>());
    }

    private void loadClaimedData() {
        claimsByWeek.clear();
        completionAwardedByWeek.clear();
        claimsLoaded = false;

        File claimedFile = new File(getDataFolder(), "claimed_data.yml");
        if (claimedFile.exists()) {
            loadClaimedDataFrom(claimedFile);
            return;
        }

        // 旧版扁平记录：只在没有新文件时迁移一次
        File legacyFile = new File(getDataFolder(), "claimed_this_week.yml");
        if (legacyFile.exists()) {
            migrateLegacyClaims(legacyFile);
            return;
        }

        claimsLoaded = true;
    }

    private void loadClaimedDataFrom(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection weeksSection = config.getConfigurationSection("weeks");

        if (weeksSection != null) {
            for (String key : weeksSection.getKeys(false)) {
                int week;
                try {
                    week = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    getLogger().warning("领取记录中存在无效的周目编号: " + key + "，已跳过");
                    continue;
                }

                ConfigurationSection players = weeksSection.getConfigurationSection(key + ".players");
                if (players != null) {
                    Map<String, ClaimRecord> records = claimsOf(week);
                    for (String playerName : players.getKeys(false)) {
                        records.put(playerName, new ClaimRecord(
                                players.getString(playerName + ".time", "-"),
                                players.getInt(playerName + ".level", -1),
                                players.getInt(playerName + ".completions", 0)));
                    }
                }

                for (String playerName : weeksSection.getStringList(key + ".completion-awarded")) {
                    awardedOf(week).add(playerName);
                }

                getLogger().info("读取第" + week + "周目领取记录: " + claimsOf(week).size() + " 人");
            }
        }

        claimsLoaded = true;
    }

    /**
     * 把旧版 claimed_this_week.yml（扁平玩家列表，没有周目信息）迁移成按周目归档的格式。
     * 归属周目由 config.yml 的 claimed-legacy-week 指定，0 表示使用当前 previous-week-data。
     */
    private void migrateLegacyClaims(File legacyFile) {
        int configuredWeek = getConfig().getInt("claimed-legacy-week", 0);
        int targetWeek = configuredWeek > 0 ? configuredWeek : previousWeekIndex;

        YamlConfiguration legacyConfig = YamlConfiguration.loadConfiguration(legacyFile);
        List<String> names = legacyConfig.getStringList("claimed");

        Map<String, Integer> weekLevels = allWeekData.get(targetWeek);
        Map<String, ClaimRecord> records = claimsOf(targetWeek);
        Set<String> awarded = awardedOf(targetWeek);
        int completionCount = 0;

        for (String name : names) {
            Integer level = weekLevels != null ? weekLevels.get(name) : null;
            if (level == null) level = previousData.get(name);
            int levelValue = level != null ? level : -1;

            int completions = levelValue >= completionThreshold ? 1 : 0;
            records.put(name, new ClaimRecord("-", levelValue, completions));
            if (completions > 0) {
                awarded.add(name);
                completionCount++;
            }
        }

        claimsLoaded = true;
        saveClaimedData();

        File backup = new File(getDataFolder(), "claimed_this_week.yml.bak");
        if (backup.exists()) {
            backup = new File(getDataFolder(), "claimed_this_week.yml." + System.currentTimeMillis() + ".bak");
        }

        if (legacyFile.renameTo(backup)) {
            getLogger().info("已将旧版领取记录迁移到第" + targetWeek + "周目："
                    + names.size() + " 人（其中 " + completionCount + " 人已计入通关次数），原文件备份为 " + backup.getName());
        } else {
            getLogger().warning("旧版领取记录已导入 claimed_data.yml，但备份 claimed_this_week.yml 失败，请手动处理");
        }

        if (!names.isEmpty()) {
            getLogger().warning("请确认上面这些记录确实属于第" + targetWeek + "周目。"
                    + "如果它们属于更早的周目（例如升级前已经切换过周目），"
                    + "请把 config.yml 的 claimed-legacy-week 设为正确编号，删除 claimed_data.yml 并还原 "
                    + backup.getName() + " 后重启。");
        }
    }

    private void saveClaimedData() {
        if (!claimsLoaded) {
            getLogger().warning("领取记录尚未成功加载，已跳过保存以避免覆盖数据");
            return;
        }

        File file = new File(getDataFolder(), "claimed_data.yml");
        YamlConfiguration config = new YamlConfiguration();

        Set<Integer> weeks = new TreeSet<>();
        weeks.addAll(claimsByWeek.keySet());
        weeks.addAll(completionAwardedByWeek.keySet());

        for (int week : weeks) {
            Map<String, ClaimRecord> records = claimsByWeek.get(week);
            if (records != null) {
                for (Map.Entry<String, ClaimRecord> entry : records.entrySet()) {
                    String path = "weeks." + week + ".players." + entry.getKey();
                    ClaimRecord record = entry.getValue();
                    config.set(path + ".time", record.time);
                    config.set(path + ".level", record.level);
                    config.set(path + ".completions", record.completions);
                }
            }

            Set<String> awarded = completionAwardedByWeek.get(week);
            if (awarded != null && !awarded.isEmpty()) {
                config.set("weeks." + week + ".completion-awarded", new ArrayList<>(awarded));
            }
        }

        try {
            config.save(file);
        } catch (Exception e) {
            getLogger().severe("保存领取记录失败: " + e.getMessage());
        }
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

        if (exportEnabled && !isClaimed(playerName, previousWeekIndex) && previousData.containsKey(playerName)) {
            int oldLevel = previousData.get(playerName);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    sendReminder(player, oldLevel);
                }
            }, 40L);
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
            player.sendMessage(getMessage("reward-disabled"));
            return;
        }

        String playerName = player.getName();

        if (isClaimed(playerName, previousWeekIndex)) {
            player.sendMessage(getMessage("already-claimed").replace("{previous_week}", previousWeek));
            return;
        }

        if (!previousData.containsKey(playerName)) {
            player.sendMessage(getMessage("no-data").replace("{previous_week}", previousWeek));
            return;
        }

        int oldLevel = previousData.get(playerName);
        // 同一玩家同一周目只累计一次通关次数：
        // 即使管理员清除了领取记录让玩家重新领奖，也不会重复 +1
        boolean firstCompletion = oldLevel >= completionThreshold
                && !isCompletionAwarded(playerName, previousWeekIndex);
        int newCompletions = firstCompletion ? 1 : 0;

        giveRewards(player, oldLevel, newCompletions);

        int currentCount = playerCompletionCount.getOrDefault(playerName, 0);
        int newTotal = currentCount + newCompletions;
        if (newCompletions > 0) {
            playerCompletionCount.put(playerName, newTotal);
            awardedOf(previousWeekIndex).add(playerName);
            saveCompletionData();
        }

        // 记录归档到“当前可领取的周目”，换周目后这里的旧记录不会影响新周目的判定
        String now = new SimpleDateFormat(timeFormat).format(new Date());
        claimsOf(previousWeekIndex).put(playerName, new ClaimRecord(now, oldLevel, newCompletions));
        saveClaimedData();

        player.sendMessage(getRawMessage("reward-header"));
        player.sendMessage(getRawMessage("reward-success")
                .replace("{previous_week}", previousWeek)
                .replace("{level}", String.valueOf(oldLevel)));

        if (newCompletions > 0) {
            player.sendMessage(getRawMessage("reward-completed").replace("{previous_week}", previousWeek));
            player.sendMessage(getRawMessage("completion-total").replace("{count}", String.valueOf(newTotal)));
        } else if (oldLevel >= completionThreshold) {
            // 已通关但通关次数发过了（例如管理员清除领取记录后重新领取），不重复累加
            player.sendMessage(getRawMessage("reward-completed-already").replace("{previous_week}", previousWeek));
            player.sendMessage(getRawMessage("completion-total").replace("{count}", String.valueOf(currentCount)));
        } else {
            player.sendMessage(getRawMessage("reward-not-completed").replace("{threshold}", String.valueOf(completionThreshold)));
        }
        player.sendMessage(getRawMessage("reward-footer"));

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private void showInfo(Player viewer) {
        buildAndOpenInfoGui(viewer, viewer.getName(), false);
    }

    private void showInfo(CommandSender sender, String targetName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only"));
            return;
        }
        buildAndOpenInfoGui((Player) sender, targetName, true);
    }

    private void buildAndOpenInfoGui(Player viewer, String targetName, boolean viewOnly) {
        int completionCount = playerCompletionCount.getOrDefault(targetName, 0);
        NamespacedKey weekKey = new NamespacedKey(this, "week_num");

        String guiTitle = viewOnly ? "§8§l" + targetName + " 的周目数据" : getRawMessage("gui-title");
        int size = 54;
        Inventory inv = Bukkit.createInventory(new InfoGuiHolder(), size, guiTitle);
        if (viewOnly) {
            ((InfoGuiHolder) inv.getHolder()).viewOnly = true;
        }

        ItemStack border = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, "§8-", null);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        String loreListKey = viewOnly ? "messages.gui-week-item-lore-view" : "messages.gui-week-item-lore";

        int slot = 9;
        for (int weekNum : detectedWeeks) {
            if (slot >= 45) break;

            Map<String, Integer> weekData = allWeekData.get(weekNum);
            boolean isPreviousWeek = (weekNum == previousWeekIndex);
            boolean hasData = weekData != null && weekData.containsKey(targetName);

            String itemName = getRawMessage("gui-week-item-name").replace("{week}", String.valueOf(weekNum));
            if (isPreviousWeek) itemName += getRawMessage("gui-previous-week-tag");

            List<String> lore = new ArrayList<>();
            if (hasData) {
                int level = weekData.get(targetName);
                boolean completed = level >= completionThreshold;

                for (String line : getConfig().getStringList(loreListKey)) {
                    line = line.replace("{level}", String.valueOf(level));
                    line = line.replace("{completed}", completed ? "§a✔" : "§c✘");
                    if (line.contains("{claimed_status}")) {
                        boolean claimed = isClaimed(targetName, weekNum);
                        String status;
                        if (claimed) {
                            status = getRawMessage("gui-claimed-tag");
                        } else if (isPreviousWeek) {
                            status = getRawMessage("gui-unclaimed-tag");
                        } else {
                            // 历史周目已经不能再领取，不能显示成“可领取”
                            status = getRawMessage("gui-archived-tag");
                        }
                        line = line.replace("{claimed_status}", status);
                    }
                    if (line.contains("{claimed_time}")) {
                        ClaimRecord record = getClaimRecord(targetName, weekNum);
                        if (record == null || "-".equals(record.time)) continue;
                        line = line.replace("{claimed_time}", record.time);
                    }
                    lore.add(line);
                }
            } else {
                for (String line : getConfig().getStringList("messages.gui-week-item-no-data-lore")) {
                    lore.add(line);
                }
            }

            if (!viewOnly && isPreviousWeek && hasData && !isClaimed(targetName, weekNum) && exportEnabled) {
                lore.add("");
                lore.add(getRawMessage("gui-click-claim"));
            }

            Material mat = hasData ? Material.PAPER : Material.GRAY_DYE;
            ItemStack weekItem = createGuiItem(mat, itemName, lore);
            setWeekTag(weekItem, weekKey, weekNum);
            inv.setItem(slot, weekItem);
            slot++;
        }

        String completionName = getRawMessage("gui-completion-item-name");
        List<String> completionLore = new ArrayList<>();
        for (String line : getConfig().getStringList("messages.gui-completion-item-lore")) {
            completionLore.add(line.replace("{count}", String.valueOf(completionCount)));
        }
        inv.setItem(49, createGuiItem(Material.NETHER_STAR, completionName, completionLore));

        viewer.openInventory(inv);
    }

    private void setWeekTag(ItemStack item, NamespacedKey key, int weekNum) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, weekNum);
            item.setItemMeta(meta);
        }
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InfoGuiHolder)) return;
        event.setCancelled(true);

        InfoGuiHolder holder = (InfoGuiHolder) event.getInventory().getHolder();
        if (holder.viewOnly) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        NamespacedKey weekKey = new NamespacedKey(this, "week_num");
        Integer weekNum = clicked.getItemMeta().getPersistentDataContainer().get(weekKey, PersistentDataType.INTEGER);
        if (weekNum == null || weekNum != previousWeekIndex) return;

        if (!exportEnabled) {
            player.sendMessage(getMessage("reward-disabled"));
            return;
        }

        String playerName = player.getName();
        if (isClaimed(playerName, previousWeekIndex)) {
            player.sendMessage(getMessage("already-claimed").replace("{previous_week}", previousWeek));
            player.closeInventory();
            return;
        }

        if (!previousData.containsKey(playerName)) {
            player.sendMessage(getMessage("no-data").replace("{previous_week}", previousWeek));
            player.closeInventory();
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) buildAndOpenInfoGui(player, player.getName(), false);
        }, 2L);

        claimReward(player);
    }

    private static class InfoGuiHolder implements InventoryHolder {
        boolean viewOnly;

        @Override
        public Inventory getInventory() { return null; }
    }

    // ============================================================
    // 奖励发放
    // ============================================================

    private void giveRewards(Player player, int oldLevel, int completions) {
        String playerName = player.getName();
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat);
        String date = sdf.format(new Date());

        addItemToInventory(player, createSouvenir(playerName, oldLevel, completions, date));

        List<Integer> sortedLevels = new ArrayList<>(levelRewards.keySet());
        sortedLevels.sort(Collections.reverseOrder());

        for (int minLevel : sortedLevels) {
            if (oldLevel >= minLevel) {
                List<RewardItem> items = levelRewards.get(minLevel);
                if (items != null) {
                    for (RewardItem reward : items) addItemToInventory(player, reward.toItemStack());
                }
                List<String> commands = levelCommands.get(minLevel);
                if (commands != null) {
                    for (String cmd : commands) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                cmd.replace("{player}", playerName)
                                        .replace("{level}", String.valueOf(oldLevel))
                                        .replace("{completions}", String.valueOf(completions))
                                        .replace("{previous_week}", previousWeek)
                                        .replace("{current_week}", currentWeek));
                    }
                }
                break;
            }
        }

        if (completions > 0) {
            for (RewardItem reward : completionRewards) addItemToInventory(player, reward.toItemStack());
            for (String cmd : completionCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("{player}", playerName)
                                .replace("{level}", String.valueOf(oldLevel))
                                .replace("{completions}", String.valueOf(completions))
                                .replace("{previous_week}", previousWeek)
                                .replace("{current_week}", currentWeek));
            }
        }
    }

    private ItemStack createSouvenir(String playerName, int level, int completions, String date) {
        ItemStack item = souvenirConfig.toItemStack();
        ItemMeta meta = item.getItemMeta();

        List<String> lore = souvenirConfig.getLore();
        if (lore != null) {
            List<String> newLore = new ArrayList<>();
            for (String line : lore) {
                newLore.add(line.replace("{player}", playerName)
                        .replace("{level}", String.valueOf(level))
                        .replace("{completions}", String.valueOf(completions))
                        .replace("{date}", date)
                        .replace("{previous_week}", previousWeek)
                        .replace("{current_week}", currentWeek));
            }
            meta.setLore(newLore);
        }

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
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim":
                return requirePlayer(sender, this::claimReward);
            case "myinfo":
                return requirePlayer(sender, this::showInfo);
            case "info":
                return requirePlayer(sender, p -> {
                    if (args.length < 2) { p.sendMessage("§c用法: /ir info <玩家名>"); return; }
                    buildAndOpenInfoGui(p, args[1], true);
                });
            case "completion":
                return handleCompletion(sender, args);
            case "record":
                return handleRecord(sender, args);
            case "export":
                if (!checkAdmin(sender)) return true;
                exportData();
                sender.sendMessage(getMessage("export-started"));
                return true;
            case "reload":
                if (!checkAdmin(sender)) return true;
                reloadPlugin(sender);
                return true;
            case "reimport":
                if (!checkAdmin(sender)) return true;
                reimportData(sender);
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("islandreward.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return false;
        }
        return true;
    }

    @FunctionalInterface
    private interface PlayerCommand { void execute(Player player); }

    private boolean requirePlayer(CommandSender sender, PlayerCommand action) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only"));
            return true;
        }
        action.execute((Player) sender);
        return true;
    }

    private boolean handleCompletion(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandreward.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendCompletionHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "add":
                if (args.length >= 4) {
                    try {
                        addCompletion(sender, args[2], Integer.parseInt(args[3]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(getMessage("not-number"));
                    }
                } else {
                    sender.sendMessage(getMessage("completion-add-usage"));
                }
                return true;

            case "clear":
                if (args.length >= 3) {
                    clearCompletionData(sender, args, 2);
                } else {
                    sender.sendMessage(getMessage("completion-clear-usage"));
                }
                return true;

            default:
                sendCompletionHelp(sender);
                return true;
        }
    }

    private boolean handleRecord(CommandSender sender, String[] args) {
        if (!sender.hasPermission("islandreward.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendRecordHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "clear":
                if (args.length >= 3) {
                    clearClaimedData(sender, args, 2);
                } else {
                    sender.sendMessage(getMessage("record-clear-usage"));
                }
                return true;

            case "info":
            case "list":
                showRecordInfo(sender, args);
                return true;

            default:
                sendRecordHelp(sender);
                return true;
        }
    }

    private void sendRecordHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§6§l▎ir record");
        sender.sendMessage("");
        sender.sendMessage("§e▸ §7领取记录管理（按周目归档，换周目不会丢失）");
        sender.sendMessage("§8  ├ §7/ir record info [周目] §f- 查看某周目的领取记录");
        sender.sendMessage("§8  ├ §7/ir record clear <玩家|all> [周目] §f- 清除领取记录");
        sender.sendMessage("§8  └ §7不填周目时默认操作当前前周目: §f" + previousWeek + "（第" + previousWeekIndex + "周目）");
        sender.sendMessage("");
    }

    /**
     * 查看某个周目的领取记录（默认当前前周目）
     */
    private void showRecordInfo(CommandSender sender, String[] args) {
        int week = previousWeekIndex;
        boolean explicitWeek = args.length >= 3;
        if (explicitWeek) {
            try {
                week = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("record-info-usage"));
                return;
            }
            if (!allWeekData.containsKey(week)) {
                sender.sendMessage(getMessage("record-week-not-found").replace("{week}", String.valueOf(week)));
                return;
            }
        }

        Map<String, ClaimRecord> records = claimsByWeek.get(week);
        Set<String> awarded = completionAwardedByWeek.get(week);

        sender.sendMessage("");
        sender.sendMessage(getRawMessage("record-info-header").replace("{week}", String.valueOf(week)));
        sender.sendMessage(getRawMessage("record-info-total").replace("{count}", String.valueOf(getClaimedCount(week))));
        sender.sendMessage(getRawMessage("record-info-awarded").replace("{count}", String.valueOf(awarded != null ? awarded.size() : 0)));

        if (records == null || records.isEmpty()) {
            sender.sendMessage(getRawMessage("record-info-none"));
        } else {
            List<String> names = new ArrayList<>(records.keySet());
            String joined;
            if (names.size() > 50) {
                joined = String.join("§7, §f", names.subList(0, 50)) + " §7...";
            } else {
                joined = String.join("§7, §f", names);
            }
            sender.sendMessage(getRawMessage("record-info-players").replace("{players}", joined));
        }
        sender.sendMessage("");
    }

    private void sendCompletionHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§6§l▎ir completion");
        sender.sendMessage("");
        sender.sendMessage("§e▸ §7通关记录管理");
        sender.sendMessage("§8  ├ §7/ir completion add <玩家> <次数> §f- 增加通关次数");
        sender.sendMessage("§8  └ §7/ir completion clear <玩家|all> §f- 清除通关记录");
        sender.sendMessage("");
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("islandreward.admin");

        sender.sendMessage("");
        sender.sendMessage(getRawMessage("help-header") + "v" + getDescription().getVersion());
        sender.sendMessage("");

        sender.sendMessage("§e▸ §7/ir claim §f- 领取 " + previousWeek + " 奖励");
        sender.sendMessage("§e▸ §7/ir myinfo §f- 打开自己的周目数据总览");
        sender.sendMessage("§e▸ §7/ir info <玩家> §f- 查看其他玩家的周目数据");

        if (isAdmin) {
            sender.sendMessage("");
            sender.sendMessage("§e▸ §7/ir completion §f- 通关记录管理");
            sender.sendMessage("§8  ├ §7/ir completion add <玩家> <次数> §f- 增加通关次数");
            sender.sendMessage("§8  └ §7/ir completion clear <玩家|all> §f- 清除通关记录");
            sender.sendMessage("");
            sender.sendMessage("§e▸ §7/ir record §f- 领取记录管理");
            sender.sendMessage("§8  ├ §7/ir record info [周目] §f- 查看领取记录");
            sender.sendMessage("§8  └ §7/ir record clear <玩家|all> [周目] §f- 清除领取记录");
            sender.sendMessage("");
            sender.sendMessage("§e▸ §7/ir export §f- 从 BentoBox 导出岛屿等级数据");
            sender.sendMessage("§e▸ §7/ir reload §f- 重载所有配置");
            sender.sendMessage("§e▸ §7/ir reimport §f- 重新导入 " + previousWeek + " 数据");
        }

        sender.sendMessage("");
        sender.sendMessage("§e▸ §7/ir help §f- 查看帮助");
    }

    private void reloadPlugin(CommandSender sender) {
        saveAllData();
        playerCompletionCount.clear();
        previousData.clear();
        allWeekData.clear();
        detectedWeeks.clear();
        claimsByWeek.clear();
        completionAwardedByWeek.clear();
        claimsLoaded = false;

        loadConfig();
        loadMessages();
        loadPlayerCompletionData();
        loadPreviousData();
        loadClaimedData();

        sender.sendMessage(getRawMessage("reload-success"));
        sender.sendMessage(getRawMessage("reload-completion").replace("{count}", String.valueOf(playerCompletionCount.size())));
        sender.sendMessage(getRawMessage("reload-data").replace("{previous_week}", previousWeek).replace("{count}", String.valueOf(previousData.size())));
        sender.sendMessage(getRawMessage("reload-claimed").replace("{count}", String.valueOf(getClaimedCount(previousWeekIndex))));
        sender.sendMessage(getRawMessage("reload-status").replace("{status}", exportEnabled ? getRawMessage("status-enabled") : getRawMessage("status-disabled")));
        sender.sendMessage(getRawMessage("reload-claimed-hint")
                .replace("{week}", String.valueOf(previousWeekIndex))
                .replace("{previous_week}", previousWeek));
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

    private void clearClaimedData(CommandSender sender, String[] args, int targetOffset) {
        if (args.length <= targetOffset) {
            sender.sendMessage(getMessage("clear-claimed-usage"));
            return;
        }

        int week = previousWeekIndex;
        boolean explicitWeek = args.length > targetOffset + 1;
        if (explicitWeek) {
            try {
                week = Integer.parseInt(args[targetOffset + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("clear-claimed-usage"));
                return;
            }
            if (!allWeekData.containsKey(week)) {
                sender.sendMessage(getMessage("record-week-not-found").replace("{week}", String.valueOf(week)));
                return;
            }
        }
        String weekName = weekLabel(week);

        if (args[targetOffset].equalsIgnoreCase("all")) {
            int count = getClaimedCount(week);
            claimsByWeek.remove(week);
            saveClaimedData();
            sender.sendMessage(getMessage("clear-claimed-all")
                    .replace("{count}", String.valueOf(count))
                    .replace("{week}", String.valueOf(week)));
            sender.sendMessage(getMessage("clear-claimed-hint").replace("{previous_week}", weekName));
            return;
        }

        String playerName = args[targetOffset];
        Map<String, ClaimRecord> records = claimsByWeek.get(week);
        if (records != null && records.containsKey(playerName)) {
            records.remove(playerName);
            saveClaimedData();
            sender.sendMessage(getMessage("clear-claimed-player").replace("{player}", playerName));
            sender.sendMessage(getMessage("clear-claimed-player-hint").replace("{previous_week}", weekName));

            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null && target.isOnline()) {
                target.sendMessage(getRawMessage("clear-claimed-notify-header"));
                target.sendMessage(getRawMessage("clear-claimed-notify-message").replace("{previous_week}", weekName));
                int level = getLevelForWeek(playerName, week);
                if (level > 0) {
                    target.sendMessage(getRawMessage("clear-claimed-notify-level").replace("{previous_week}", weekName).replace("{level}", String.valueOf(level)));
                }
                target.sendMessage(getRawMessage("clear-claimed-notify-command"));
                target.sendMessage(getRawMessage("clear-claimed-notify-footer"));
            }
        } else {
            sender.sendMessage(getMessage("clear-claimed-not-found").replace("{player}", playerName));
        }
    }

    /**
     * 周目显示名：当前前周目用配置里的名称，其他周目用“第N周目”
     */
    private String weekLabel(int week) {
        return week == previousWeekIndex ? previousWeek : "第" + week + "周目";
    }

    private void clearCompletionData(CommandSender sender, String[] args, int targetOffset) {
        if (args.length <= targetOffset) {
            sender.sendMessage(getMessage("clear-completion-usage"));
            return;
        }

        if (args[targetOffset].equalsIgnoreCase("all")) {
            int count = playerCompletionCount.size();
            playerCompletionCount.clear();
            saveCompletionData();
            sender.sendMessage(getMessage("clear-completion-all").replace("{count}", String.valueOf(count)));
            return;
        }

        String playerName = args[targetOffset];
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
    public int getLevelForWeek(String playerName, int weekNum) {
        Map<String, Integer> weekData = allWeekData.get(weekNum);
        return weekData != null ? weekData.getOrDefault(playerName, -1) : -1;
    }
    public boolean isClaimed(String playerName) { return isClaimed(playerName, previousWeekIndex); }
    public boolean isClaimed(String playerName, int weekNum) {
        Map<String, ClaimRecord> records = claimsByWeek.get(weekNum);
        return records != null && records.containsKey(playerName);
    }
    public ClaimRecord getClaimRecord(String playerName, int weekNum) {
        Map<String, ClaimRecord> records = claimsByWeek.get(weekNum);
        return records != null ? records.get(playerName) : null;
    }
    public int getClaimedCount(int weekNum) {
        Map<String, ClaimRecord> records = claimsByWeek.get(weekNum);
        return records != null ? records.size() : 0;
    }
    public boolean isCompletionAwarded(String playerName, int weekNum) {
        Set<String> awarded = completionAwardedByWeek.get(weekNum);
        return awarded != null && awarded.contains(playerName);
    }
    public String getPreviousWeek() { return previousWeek; }
    public String getCurrentWeek() { return currentWeek; }
    public boolean isExportEnabled() { return exportEnabled; }
    public List<Integer> getDetectedWeeks() { return new ArrayList<>(detectedWeeks); }

    // ============================================================
    // 内部类
    // ============================================================

    /**
     * 单个玩家的领取记录（归属于某一个周目）
     */
    public static class ClaimRecord {
        private final String time;       // 领取时间
        private final int level;         // 领取时的周目等级
        private final int completions;   // 本次加的通关次数（0 或 1）

        public ClaimRecord(String time, int level, int completions) {
            this.time = time;
            this.level = level;
            this.completions = completions;
        }

        public String getTime() { return time; }
        public int getLevel() { return level; }
        public int getCompletions() { return completions; }
    }

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