# IslandReward

IslandReward 是一款 Minecraft 服务器插件，为基于 BentoBox 的岛屿系统提供**周目奖励**和**通关次数统计**功能。玩家可根据上一周目的岛屿等级领取奖励，达到通关阈值还可获得额外奖励和纪念品。

## ✨ 功能特性

- **周目奖励系统**：玩家领取上一周目的等级奖励
- **通关阈值机制**：达到指定等级可增加通关次数
- **纪念品系统**：领取奖励时自动发放带玩家信息的个性化纪念品
- **多档位等级奖励**：根据等级区间发放不同物品和命令奖励
- **BentoBox 数据导出**：自动从 BentoBox 数据库导出岛屿等级数据
- **PlaceholderAPI 支持**：提供丰富的变量用于计分板、聊天等
- **数据持久化**：自动保存通关次数和领取记录
- **全消息可配置**：支持自定义所有提示消息

## 📦 安装方法

1. 将 `IslandReward.jar` 放入服务器的 `plugins/` 目录
2. 确保服务器已安装 **PlaceholderAPI**（可选，用于变量）
3. 确保服务器已安装 **BentoBox**（用于数据导出）
4. 重启服务器或使用 `plugman load IslandReward` 加载插件
5. 编辑 `plugins/IslandReward/config.yml` 进行配置
6. 使用 `/ir export` 导出上一周目的岛屿等级数据

## 🛠️ 命令

### 玩家命令

| 命令 | 描述 |
|------|------|
| `/ir claim` | 领取上一周目奖励 |
| `/ir info` | 查看上一周目信息（等级、通关状态等） |
| `/ir mycompletion` | 查看累计通关次数 |
| `/ir help` | 查看帮助信息 |

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/ir export` | 从 BentoBox 导出岛屿等级数据 | `islandreward.admin` |
| `/ir reload` | 重载配置文件 | `islandreward.admin` |
| `/ir check <玩家>` | 查看指定玩家的通关次数和数据 | `islandreward.admin` |
| `/ir add <玩家> <次数>` | 增加玩家的通关次数 | `islandreward.admin` |
| `/ir reimport` | 重新导入上一周目数据 | `islandreward.admin` |
| `/ir clear <玩家\|all>` | 清除玩家的奖励领取记录 | `islandreward.admin` |
| `/ir clearcompletion <玩家\|all>` | 清除玩家的通关记录 | `islandreward.admin` |

**快捷别名**：`/ir` 和 `/islandreward` 均可使用

## 🔌 PlaceholderAPI 变量

本插件支持 PlaceholderAPI，你可以在其他插件中使用以下变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `%islandreward_completion%` | 累计通关次数 | 1 |
| `%islandreward_threshold%` | 通关阈值 | 10000 |
| `%islandreward_previous_level%` | 前周目等级 | 15000 |
| `%islandreward_claimed%` | 是否已领取 | true |
| `%islandreward_claimed_status%` | 领取状态 | §a已领取 |
| `%islandreward_completed%` | 是否通关 | true |
| `%islandreward_completed_status%` | 通关状态 | §a已通关 |
| `%islandreward_previous_week%` | 前周目名称 | 第一周目 |
| `%islandreward_current_week%` | 当前周目名称 | 第二周目 |
| `%islandreward_export_enabled%` | 奖励领取开关 | true |

### 使用示例

在计分板或公告中显示：

| 代码 | 说明 |
|------|------|
| `§7前周目等级: §a%islandreward_previous_level%` | 显示前周目等级 |
| `§7通关状态: %islandreward_completed_status%` | 显示通关状态（已通关/未通关） |
| `§7累计通关: §e%islandreward_completion% §7次` | 显示累计通关次数 |

## ⚙️ 配置说明

配置文件位于 `plugins/IslandReward/config.yml`

### 基础配置

```yaml
# 奖励领取开关及导出配置
export:
  enabled: false          # 是否允许玩家领取奖励
  data-file: "island_data.yml"  # 导出数据文件名

# 周目配置
completion-threshold: 10000   # 通关所需等级阈值
current-week: "第二周目"       # 当前周目名称
previous-week: "第一周目"      # 前周目名称
time-format: "yyyy-MM-dd HH:mm:ss"  # 纪念品时间格式
```

### 奖励配置
```yaml
rewards:
# 纪念品配置
souvenir:
material: NAME_TAG
name: "§6§l⭐ {player} 的 {previous_week} 纪念品 §6§l⭐"
lore:
- "§7这是你在 {previous_week} 空岛生存的成就证明"
- "§7玩家ID: §f{player}"
- "§e📊 {previous_week} 等级: §6{level} §7级"
# ... 更多描述

# 等级区间奖励
level-rewards:
10000:    # ≥10000级
items:
- material: DIAMOND
amount: 64
commands:
- "broadcast §6恭喜 {player} 通关！"

# 通关奖励（达到通关阈值额外发放）
completion:
items:
- material: DRAGON_EGG
amount: 1
commands:
- "broadcast §6恭喜 {player} 通关次数 +1"
```

### 奖励变量说明

在奖励命令中可使用以下变量：

| 变量 | 说明 |
|------|------|
| `{player}` | 玩家名 |
| `{level}` | 前周目等级 |
| `{completions}` | 本次增加的通关次数 |
| `{previous_week}` | 前周目名称 |
| `{current_week}` | 当前周目名称 |

## 📂 数据文件

插件会在 `plugins/IslandReward/` 目录下生成以下文件：

| 文件 | 说明 |
|------|------|
| `config.yml` | 主配置文件 |
| `island_data.yml` | 导出的玩家等级数据（名称可自定义） |
| `completion_data.yml` | 玩家累计通关次数 |
| `claimed_this_week.yml` | 已领取奖励的玩家列表 |

## 📝 使用流程

### 周目更替时的操作流程

1. 关闭服务器，备份数据
2. 修改 `config.yml`：
    - 将 `previous-week` 改为刚结束的周目名称
    - 将 `current-week` 改为新周目名称
    - 将 `export.enabled` 设为 `true`
3. 启动服务器，执行 `/ir export` 导出上一周目数据
4. 数据导出完成后，执行 `/ir reload` 重载配置
5. 玩家进入服务器时会收到领取提醒，使用 `/ir claim` 领取奖励

### 数据导出说明

导出命令会从 BentoBox 数据库读取以下信息：

| 路径 | 说明 |
|------|------|
| `plugins/BentoBox/database/Island/*.json` | 岛屿所有者信息 |
| `plugins/BentoBox/database/Players/*.json` | 玩家名称映射 |
| `plugins/BentoBox/database/IslandLevels/*.json` | 岛屿等级数据 |

导出后生成 island_data.yml，格式为：

```yaml
玩家名:
level: 等级
```

## ❓ 常见问题

**Q: 如何开始使用？**  
A: 配置好 `config.yml` 后，执行 `/ir export` 导出数据，然后玩家即可用 `/ir claim` 领取奖励。

**Q: 玩家如何查看自己的数据？**  
A: 使用 `/ir info` 查看前周目等级和通关状态，使用 `/ir mycompletion` 查看累计通关次数。

**Q: 通关次数有什么用？**  
A: 通关次数是累计统计，用于记录玩家完成周目的次数，可在计分板中展示作为荣誉。

**Q: 如何重置玩家的领取资格？**  
A: 使用 `/ir clear <玩家名>` 清除指定玩家的领取记录，或 `/ir clear all` 清除所有记录。

**Q: 如何修改奖励内容？**  
A: 编辑 `config.yml` 中的 `rewards` 部分，然后执行 `/ir reload` 重载配置。

**Q: 插件支持哪些岛屿插件？**  
A: 目前支持 BentoBox 系列的岛屿插件（BSkyBlock、ASkyBlock 等）。

## 🔧 权限节点

| 权限节点 | 描述 | 默认 |
|----------|------|------|
| `islandreward.admin` | 允许使用所有管理员命令 | op |

## 📄 许可证

当前仓库未声明开源许可证，使用前请先联系作者获取授权。

## 🙏 致谢

- 感谢 BentoBox 团队提供的 API
- 感谢 PlaceholderAPI 团队提供的变量扩展支持