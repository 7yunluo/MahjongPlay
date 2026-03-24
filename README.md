# MahjongCraft Plugin

A Paper server plugin that brings full Japanese Riichi Mahjong to Minecraft. Players interact with 3D mahjong tiles on a physical table using right-click entity interactions — no client mod required.

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

## Requirements

- Paper 1.21.4+ (tested on Leaves core 1.21.8)
- Java 21
- Server resource pack with mahjong tile models (included in `resource-pack/`)

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mahjong create [one/east/twowind/three]` | OP | Create a table |
| `/mahjong destroy [table-id]` | OP | Destroy a table |
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

Output: `build/libs/MahjongCraft-1.0.0.jar`

## Credits

- Original mod: [MahjongCraft](https://github.com/doublemoon1119/MahjongCraft) by doublemoon1119
- Mahjong logic: [mahjong4j](https://github.com/mahjong4j/mahjong4j)

## License

MIT
