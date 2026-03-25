# IslandReward

IslandReward is a Minecraft server plugin that provides weekly rewards and achievement systems for island-based games (such as ASkyBlock, BentoBox, etc.).

## Features

- **Weekly Reward System**: Players can claim one island reward per week.
- **Island Level Rewards**: Different rewards are granted based on island levels.
- **Completion Rewards**: Players receive special rewards after reaching a certain number of completions.
- **Souvenir System**: Players can obtain souvenirs with personalized information.
- **PlaceholderAPI Support**: Variables can be used in chat, scoreboards, and other interfaces.
- **Data Export**: Supports exporting player data in YAML format.
- **Multi-language Messages**: Customizable message content via configuration.

## Supported Plugins

- PlaceholderAPI 2.11.3+

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/islandreward claim` | Claim this week's reward |
| `/islandreward info` | View reward information |
| `/islandreward mycompletion` | View personal completion progress |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/islandreward reload` | Reload the configuration file |
| `/islandreward check <player>` | View player data |
| `/islandreward addcompletion <player> <count>` | Add completion count |
| `/islandreward reimport` | Re-import data |
| `/islandreward clearclaimed [player]` | Clear this week's claimed records |
| `/islandreward clearcompletion [player]` | Clear completion data |

## Installation

1. Place the compiled `IslandReward.jar` file into the server's `plugins` folder.
2. Ensure the server has the **PlaceholderAPI** plugin installed.
3. Restart the server or use `/plugins` to confirm the plugin is loaded.
4. Modify the `config.yml` file as needed.

## Configuration

The plugin configuration file is located at `plugins/IslandReward/config.yml` and includes:

- `completion-threshold`: Number of completions required to claim a reward
- `export-enabled`: Enable or disable data export
- `export-data-file`: Path for exported data file
- Island level reward configurations
- Completion reward configurations
- Souvenir item configurations

## PlaceholderAPI Variables

Use `{islandreward_completion}` to retrieve the player's completion count.

## License

This plugin is intended solely for personal learning and communication purposes.