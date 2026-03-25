

# IslandReward

IslandReward 是一款 Minecraft 服务器插件，为基于 Island（岛屿）系统的游戏（如 ASkyBlock、BentoBox 等）提供周奖励和成就系统。

## 功能特性

- **周奖励系统**: 玩家每周可领取一次岛屿奖励
- **岛屿等级奖励**: 根据岛屿等级发放不同奖励
- **完成度奖励**: 玩家完成一定次数后可领取特殊奖励
- **纪念品系统**: 玩家可获得带有个人信息的纪念品
- **PlaceholderAPI 支持**: 支持在聊天、计分板等处使用变量
- **数据导出**: 支持将玩家数据导出为 YAML 格式
- **多语言消息**: 可自定义配置消息内容

## 支持的插件

- PlaceholderAPI 2.11.3+

## 命令说明

### 玩家命令

| 命令 | 描述 |
|------|------|
| `/islandreward claim` | 领取本周奖励 |
| `/islandreward info` | 查看奖励信息 |
| `/islandreward mycompletion` | 查看个人完成度 |

### 管理员命令

| 命令 | 描述 |
|------|------|
| `/islandreward reload` | 重载配置文件 |
| `/islandreward check <玩家>` | 查看玩家数据 |
| `/islandreward addcompletion <玩家> <数量>` | 添加完成次数 |
| `/islandreward reimport` | 重新导入数据 |
| `/islandreward clearclaimed [玩家]` | 清空本周已领取记录 |
| `/islandreward clearcompletion [玩家]` | 清空完成度数据 |

## 安装说明

1. 将编译好的 `IslandReward.jar` 文件放入服务器的 `plugins` 文件夹
2. 确保服务器已安装 **PlaceholderAPI** 插件
3. 重启服务器或使用 `/plugins` 确认插件已加载
4. 根据需要修改 `config.yml` 配置文件

## 配置说明

插件配置文件位于 `plugins/IslandReward/config.yml`，主要包含：

- `completion-threshold`: 领取奖励所需的完成次数
- `export-enabled`: 是否启用数据导出
- `export-data-file`: 导出文件路径
- 岛屿等级奖励配置
- 完成度奖励配置
- 纪念品物品配置

## PlaceholderAPI 变量

使用 `{islandreward_completion}` 获取玩家完成次数

## 许可证

本插件仅供个人学习交流使用。