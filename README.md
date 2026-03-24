# MahjongPlay

一个纯服务端的 Paper 插件，在 Minecraft 中实现完整的日本立直麻将。玩家通过右键点击 3D 麻将牌实体进行游戏，无需安装任何客户端 Mod。

移植自 Fabric 模组 [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft)（作者：doublemoon1119）
并在此基础上加入了三人麻将的玩法和更多适合服务器的便利功能。

<details>
<summary>截图演示</summary>
<img width="2536" height="1508" alt="Screenshot from 2026-03-24 18-23-38" src="https://github.com/user-attachments/assets/61f952f3-df14-41cd-a065-54dd5894d7c1" />

<img width="2358" height="1361" alt="Screenshot from 2026-03-24 18-22-36" src="https://github.com/user-attachments/assets/58314e0a-05b1-4874-a2c7-d010fa00a296" />

<img width="2551" height="1514" alt="Screenshot from 2026-03-24 18-24-03" src="https://github.com/user-attachments/assets/7115de82-4ed6-43af-9869-9a3852370e9e" />

<img width="1349" height="908" alt="Screenshot from 2026-03-24 18-25-31" src="https://github.com/user-attachments/assets/9752c28c-055b-4959-b6b6-36b43bd1f0d4" />

</details>

## 功能特性

- **纯服务端** — 玩家只需接受服务器资源包即可游玩
- **3D 麻将牌** — 使用 ItemDisplay 实体在 3×3 牌桌上展示立体麻将牌
- **右键交互** — 出牌、吃、碰、杠、立直、荣和、自摸等所有操作均通过右键点击完成
- **多种模式** — 支持四麻（半庄/东风/一局）和三麻，规则完整
- **三人麻将（三麻）** — 去除二万~八万、禁止吃、拔北（抜きドラ）、自摸损计分
- **两步出牌确认** — 第一次点击抬起麻将牌，第二次点击确认出牌，同时高亮牌河中相同的牌
- **TextDisplay 操作按钮** — 吃/碰/杠等操作以悬浮按钮形式显示，支持二级子菜单
- **Boss 血条** — 常驻显示场风、牌山剩余、所有玩家风位/名字/倒计时
- **ActionBar HUD** — 实时显示局数、本场、宝牌、点数、听牌提示
- **Title 通知** — 吃/碰/杠/立直/自摸/荣和/流局等事件以屏幕中央标题显示
- **牌桌持久化** — 服务器重启后牌桌自动恢复
- **自动开始** — 所有玩家准备后 3 秒倒计时自动开始，空位自动补机器人
- **牌桌保护** — 玩家无法破坏牌桌方块
- **中文役种名** — 所有役种以中文显示，支持番/符/满贯等计分展示
- **赤宝牌** — 默认包含 3 张赤宝牌（赤五万/赤五筒/赤五索）

## 环境要求

- Paper 1.21.4+（已在 Leaves 核心 1.21.8 上测试）
- Java 21
- 服务器资源包（麻将牌模型和贴图，已包含在 `resource-pack/` 目录中）

## 指令

| 指令 | 权限 | 说明 |
|------|------|------|
| `/mahjong create [one/east/twowind/three]` | OP | 创建牌桌（一局/东风/半庄/三麻） |
| `/mahjong destroy [牌桌ID]` | OP | 销毁指定牌桌（支持 Tab 补全） |
| `/mahjong bot` | OP | 添加机器人 |
| `/mahjong kick <座位号>` | OP | 踢出玩家 |
| `/mahjong start` | OP | 强制开始游戏 |
| `/mahjong join [id]` | 所有人 | 加入牌桌 |
| `/mahjong leave` | 所有人 | 离开牌桌 |
| `/mahjong ready / unready` | 所有人 | 准备/取消准备 |
| `/mahjong list` | 所有人 | 查看所有牌桌 |
| `/mahjong info` | 所有人 | 查看当前牌桌信息 |

## 构建

```bash
./gradlew shadowJar
```

输出：`build/libs/MahjongPlay-1.0.0.jar`

## 致谢

- 原版模组：[MahjongCraft](https://github.com/doublemoon1119/MahjongCraft)（作者：doublemoon1119）
- 麻将逻辑库：[mahjong4j](https://github.com/mahjong4j/mahjong4j)

## 许可证

MIT

---

<details>
<summary>English</summary>

# MahjongPlay

A pure server-side Paper plugin that brings full Japanese Riichi Mahjong to Minecraft. Players interact with 3D mahjong tiles on a physical table using right-click entity interactions — no client mod required.

Ported from the Fabric mod [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft) by doublemoon1119.

## Features

- **Pure server-side** — players only need to accept the server resource pack
- **3D tile display** using ItemDisplay entities on a 3×3 table
- **Right-click interaction** for all actions (discard, chii, pon, kan, riichi, ron, tsumo)
- **4-player (半庄/東風/一局) and 3-player (三麻) modes** with full rule support
- **3-player mahjong (三麻)**: removed 2-8 manzu tiles, disabled chii, nukidora (拔北), tsumo loss scoring
- **Two-click discard confirmation** with discard pile highlight
- **TextDisplay action buttons** with two-level sub-menus
- **Boss bar** showing all players' wind/name/timer, round info, and wall count
- **ActionBar HUD** with round, dora, points, and tenpai indicator
- **Title notifications** for game events (chii/pon/kan/riichi/tsumo/ron/draw)
- **Persistent tables** that survive server restarts
- **Auto-start** with 3-second countdown when all players are ready
- **Auto-fill bots** for empty seats on game start
- **Table protection** — blocks cannot be broken by players
- **Chinese yaku names** and score display (fu/han/mangan naming)
- **Red fives** — 3 red five tiles enabled by default

## Requirements

- Paper 1.21.4+ (tested on Leaves core 1.21.8)
- Java 21
- Server resource pack with mahjong tile models (included in `resource-pack/`)

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mahjong create [one/east/twowind/three]` | OP | Create a table |
| `/mahjong destroy [table-id]` | OP | Destroy a table (tab-complete) |
| `/mahjong bot` | OP | Add a bot |
| `/mahjong kick <seat>` | OP | Kick a player |
| `/mahjong start` | OP | Force start |
| `/mahjong join [id]` | All | Join a table |
| `/mahjong leave` | All | Leave a table |
| `/mahjong ready / unready` | All | Toggle ready |
| `/mahjong list` | All | List all tables |
| `/mahjong info` | All | Show table info |

## Building

```bash
./gradlew shadowJar
```

Output: `build/libs/MahjongPlay-1.0.0.jar`

## Credits

- Original mod: [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft) by doublemoon1119
- Mahjong logic: [mahjong4j](https://github.com/mahjong4j/mahjong4j)

## License

MIT

</details>
