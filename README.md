# IslandReward

IslandReward 是一款 Minecraft 服务器插件，为基于 BentoBox 的岛屿系统提供**多周目奖励**、**通关次数统计**和**数据总览**功能。支持多周目数据独立管理、GUI 菜单查看、PlaceholderAPI 变量扩展。

## ✨ 功能特性

- **多周目数据管理**：自动检测 `island_data_N.yml` 文件，支持在配置中切换当前前周目
- **GUI 数据总览**：`/ir myinfo` 打开菜单查看所有周目数据，点击快捷领取
- **玩家互看**：`/ir info <玩家>` 查看其他玩家的周目进度
- **周目奖励系统**：玩家领取配置的前周目等级奖励
- **通关阈值机制**：达到指定等级可增加通关次数
- **纪念品系统**：领取奖励时自动发放带玩家信息的个性化纪念品
- **多档位等级奖励**：根据等级区间发放不同物品和命令奖励
- **BentoBox 数据导出**：自动从 BentoBox 数据库导出岛屿等级数据
- **PlaceholderAPI 支持**：提供指定周目的变量，支持 `_N` 后缀
- **数据持久化**：自动保存通关次数和领取记录
- **全消息可配置**：支持自定义所有提示消息
- **配置自动补全**：升级插件时自动补全缺失的配置项

## 📦 安装方法

1. 将 `IslandReward-2.0.jar` 放入服务器的 `plugins/` 目录
2. 确保服务器已安装 **PlaceholderAPI**（可选，用于变量）
3. 确保服务器已安装 **BentoBox**（用于数据导出）
4. 重启服务器或使用 `plugman load IslandReward` 加载插件
5. 编辑 `plugins/IslandReward/config.yml` 进行配置
6. 使用 `/ir export` 导出岛屿等级数据，手动重命名为 `island_data_N.yml`

## 🛠️ 命令

### 玩家命令

| 命令 | 描述 |
|------|------|
| `/ir claim` | 领取当前配置的前周目奖励 |
| `/ir myinfo` | 打开自己的周目数据总览 GUI |
| `/ir info <玩家>` | 查看其他玩家的周目数据（只读） |

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/ir completion add <玩家> <次数>` | 增加玩家的通关次数 | `islandreward.admin` |
| `/ir completion clear <玩家\|all>` | 清除通关记录 | `islandreward.admin` |
| `/ir record clear <玩家\|all>` | 清除奖励领取记录 | `islandreward.admin` |
| `/ir export` | 从 BentoBox 导出岛屿等级数据 | `islandreward.admin` |
| `/ir reload` | 重载配置与数据文件 | `islandreward.admin` |
| `/ir reimport` | 重新从文件加载周目数据 | `islandreward.admin` |
| `/ir help` | 查看帮助信息 | - |

**快捷别名**：`/ir` 和 `/islandreward` 均可使用

## 🔌 PlaceholderAPI 变量

本插件支持 PlaceholderAPI，你可以在其他插件中使用以下变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `%islandreward_completion%` | 累计通关次数 | 1 |
| `%islandreward_threshold%` | 通关阈值 | 10000 |
| `%islandreward_previous_level%` | 当前配置前周目的等级 | 15000 |
| `%islandreward_previous_level_N%` | 指定第 N 周目的等级 | 如 `previous_level_1` |
| `%islandreward_claimed%` | 是否已领取 | true |
| `%islandreward_claimed_status%` | 领取状态 | §a已领取 |
| `%islandreward_completed%` | 当前配置前周目是否通关 | true |
| `%islandreward_completed_N%` | 指定第 N 周目是否通关 | 如 `completed_1` |
| `%islandreward_completed_status%` | 当前配置前周目通关状态 | §a已通关 |
| `%islandreward_completed_status_N%` | 指定第 N 周目通关状态 | 如 `completed_status_1` |
| `%islandreward_previous_week%` | 前周目名称 | 第一周目 |
| `%islandreward_current_week%` | 当前周目名称 | 第二周目 |
| `%islandreward_export_enabled%` | 奖励领取开关 | true |

### 指定周目变量

带 `_N` 后缀的变量可查询任意周目数据：

```
%islandreward_previous_level_1%   → 第 1 周目的等级
%islandreward_previous_level_2%   → 第 2 周目的等级
%islandreward_completed_1%        → 第 1 周目是否通关
%islandreward_completed_status_1% → 第 1 周目通关状态文字
```

不加后缀时默认查询当前配置的前周目，向后兼容旧版。

### 使用示例

```
§7前周目等级: §a%islandreward_previous_level%
§7第1周目等级: §a%islandreward_previous_level_1%
§7通关状态: %islandreward_completed_status%
§7累计通关: §e%islandreward_completion% §7次
```

## ⚙️ 配置说明

配置文件位于 `plugins/IslandReward/config.yml`

### 基础配置

```yaml
# 奖励领取开关及导出配置
export:
  enabled: false              # 是否允许玩家领取奖励
  data-file: "island_data.yml" # 导出数据文件名

# 周目配置
completion-threshold: 10000    # 通关所需等级阈值
current-week: "第二周目"        # 当前周目名称
previous-week: "第一周目"       # 前周目名称
previous-week-data: 1          # 当前读取的上个周目文件编号 (对应 island_data_N.yml)
time-format: "yyyy-MM-dd HH:mm:ss"
```

### 奖励配置

```yaml
rewards:
  souvenir:
    material: NAME_TAG
    name: "§6§l⭐ {player} 的 {previous_week} 纪念品 §6§l⭐"
    lore:
      - "§7这是你在 {previous_week} 空岛生存的成就证明"
      # ...

  level-rewards:
    10000:
      items:
        - material: DIAMOND
          amount: 64
      commands:
        - "broadcast §6恭喜 {player} 通关！"

  completion:
    items:
      - material: DRAGON_EGG
        amount: 1
    commands:
      - "broadcast §6恭喜 {player} 通关次数 +1"
```

### 奖励变量

| 变量 | 说明 |
|------|------|
| `{player}` | 玩家名 |
| `{level}` | 前周目等级 |
| `{completions}` | 本次增加的通关次数 |
| `{previous_week}` | 前周目名称 |
| `{current_week}` | 当前周目名称 |
| `{date}` | 颁发时间 |

## 📂 数据文件

插件会在 `plugins/IslandReward/` 目录下生成以下文件：

| 文件 | 说明 |
|------|------|
| `config.yml` | 主配置文件 |
| `island_data_1.yml` | 第 1 周目等级数据 |
| `island_data_2.yml` | 第 2 周目等级数据 |
| `island_data_N.yml` | 以此类推 |
| `completion_data.yml` | 玩家累计通关次数 |
| `claimed_this_week.yml` | 已领取奖励的玩家列表 |

## 📝 使用流程

### 周目更替时的操作流程

1. 关闭服务器，备份数据
2. 修改 `config.yml`：
   - 将 `previous-week` 改为刚结束的周目名称
   - 将 `current-week` 改为新周目名称
   - 将 `previous-week-data` 设为对应的周目文件编号
   - 将 `export.enabled` 设为 `true`
3. 启动服务器，执行 `/ir export` 导出上一周目数据
4. 将导出的文件重命名为 `island_data_N.yml`（N 为周目编号）
5. 执行 `/ir reload` 重载配置
6. 玩家进入服务器时会收到领取提醒，使用 `/ir claim` 领取奖励

### 多周目数据管理

导出各周目数据后，将文件命名为 `island_data_1.yml`、`island_data_2.yml` 等放入插件目录。插件启动时自动检测所有周目文件，在 `config.yml` 中修改 `previous-week-data` 即可切换当前允许领取的周目。

玩家使用 `/ir myinfo` 可在 GUI 中查看全部周目数据。

### 数据导出说明

导出命令会从 BentoBox 数据库读取以下信息：

| 路径 | 说明 |
|------|------|
| `plugins/BentoBox/database/Island/*.json` | 岛屿所有者信息 |
| `plugins/BentoBox/database/Players/*.json` | 玩家名称映射 |
| `plugins/BentoBox/database/IslandLevels/*.json` | 岛屿等级数据 |

导出后生成 YAML 文件，格式为：

```yaml
玩家名:
  level: 等级
```

## ❓ 常见问题

**Q: 如何开始使用？**  
A: 配置好 `config.yml` 后，执行 `/ir export` 导出数据并重命名为 `island_data_N.yml`，玩家即可用 `/ir claim` 领取奖励。

**Q: 玩家如何查看自己的数据？**  
A: 使用 `/ir myinfo` 打开 GUI 菜单查看全部周目数据，点击"上个周目"物品可快捷领取奖励。

**Q: 玩家如何互看进度？**  
A: 使用 `/ir info <玩家名>` 查看其他玩家的周目数据（只读，不显示领取状态）。

**Q: 通关次数有什么用？**  
A: 通关次数是累计统计，用于记录玩家完成周目的次数，可在计分板中展示作为荣誉。

**Q: 如何重置玩家的领取资格？**  
A: 使用 `/ir record clear <玩家名>` 清除指定玩家的领取记录，或 `/ir record clear all` 清除所有记录。

**Q: 如何修改奖励内容？**  
A: 编辑 `config.yml` 中的 `rewards` 部分，然后执行 `/ir reload` 重载配置。

**Q: 插件支持哪些岛屿插件？**  
A: 目前支持 BentoBox 系列的岛屿插件（BSkyBlock 等）。

**Q: 更新插件版本后如何迁移配置？**  
A: 插件启动时会自动检测并补全缺失的配置项，无需手动删除重建 config.yml。

## 🔧 权限节点

| 权限节点 | 描述 | 默认 |
|----------|------|------|
| `islandreward.admin` | 允许使用所有管理员命令 | op |

## 📄 许可证

当前仓库未声明开源许可证，使用前请先联系作者获取授权。

## 🙏 致谢

- 感谢 BentoBox 团队提供的 API
- 感谢 PlaceholderAPI 团队提供的变量扩展支持
