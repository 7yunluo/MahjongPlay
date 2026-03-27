package com.mahjongplay.table

import com.mahjongplay.game.GameStatus
import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.model.MahjongRule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MahjongCommand(private val manager: MahjongTableManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "join" -> handleJoin(sender, args)
            "leave" -> handleLeave(sender)
            "ready" -> handleReady(sender, true)
            "unready" -> handleReady(sender, false)
            "start" -> handleStart(sender)
            "bot" -> handleAddBot(sender)
            "kick" -> handleKick(sender, args)
            "destroy" -> handleDestroy(sender, args)
            "action" -> handleAction(sender, args)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender)
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleCreate(player: Player, args: Array<out String>) {
        if (!player.isOp) {
            player.msg("只有管理员可以创建麻将桌", NamedTextColor.RED)
            return
        }
        val gameLength = when (args.getOrNull(1)?.lowercase()) {
            "one" -> MahjongRule.GameLength.ONE_GAME
            "east" -> MahjongRule.GameLength.EAST
            "three" -> null
            "twowind", null -> MahjongRule.GameLength.TWO_WIND
            else -> {
                player.msg("可选模式: one(一局) / east(东风) / twowind(半庄) / three(三麻半庄)", NamedTextColor.RED)
                return
            }
        }
        val isSanma = args.getOrNull(1)?.lowercase() == "three"
        val actualLength = gameLength ?: MahjongRule.GameLength.TWO_WIND
        val actualPlayerCount = if (isSanma) 3 else 4
        val startingPoints = if (isSanma) 35000 else 25000
        val direction = player.location.direction.setY(0).normalize()
        val loc = player.location.clone().add(direction.multiply(4))
        val center = loc.clone()
        center.x = loc.blockX + 0.5
        center.y = loc.blockY.toDouble()
        center.z = loc.blockZ + 0.5
        val session = manager.createTable(center, player.uniqueId.toString(), player.name, actualLength, actualPlayerCount, startingPoints)
        session.table.spawn()
        manager.registerJoinInteraction(session)
        player.msg("麻将桌已创建! ${session.humanId}", NamedTextColor.GREEN)
        player.msg("使用 /mahjong bot 添加机器人, /mahjong start 开始游戏", NamedTextColor.YELLOW)
    }

    private fun handleJoin(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            val sessions = manager.getAllSessions()
            if (sessions.isEmpty()) {
                player.msg("没有可用的麻将桌", NamedTextColor.RED)
                return
            }
            val first = sessions.first()
            if (manager.joinTable(first.tableId, player.uniqueId.toString(), player.name)) {
                player.msg("已加入麻将桌 ${first.tableId.toString().take(8)}", NamedTextColor.GREEN)
            } else {
                player.msg("无法加入 (可能已满或你已在游戏中)", NamedTextColor.RED)
            }
            return
        }

        val tableIdStr = args[1]
        val matchingSession = manager.getAllSessions().find { it.tableId.toString().startsWith(tableIdStr) }
        if (matchingSession == null) {
            player.msg("找不到桌子: $tableIdStr", NamedTextColor.RED)
            return
        }
        if (manager.joinTable(matchingSession.tableId, player.uniqueId.toString(), player.name)) {
            player.msg("已加入麻将桌", NamedTextColor.GREEN)
        } else {
            player.msg("无法加入 (可能已满或你已在游戏中)", NamedTextColor.RED)
        }
    }

    private fun handleLeave(player: Player) {
        if (manager.leaveTable(player.uniqueId.toString())) {
            player.msg("已离开麻将桌", NamedTextColor.YELLOW)
        } else {
            player.msg("你不在任何麻将桌中", NamedTextColor.RED)
        }
    }

    private fun handleReady(player: Player, ready: Boolean) {
        val session = manager.getSessionForPlayer(player.uniqueId.toString())
        if (session == null) {
            player.msg("你不在任何麻将桌中", NamedTextColor.RED)
            return
        }
        session.game.readyOrNot(player.uniqueId.toString(), ready)
        manager.updateTableDisplay(session)
        player.msg(if (ready) "已准备" else "取消准备", NamedTextColor.GREEN)
        manager.checkAutoStart(session)
    }

    private fun handleStart(player: Player) {
        if (!player.isOp) {
            player.msg("只有管理员可以强制开始游戏", NamedTextColor.RED)
            return
        }
        val session = manager.getSessionForPlayer(player.uniqueId.toString())
            ?: manager.getAllSessions().firstOrNull()
        if (session == null) {
            player.msg("没有可用的麻将桌", NamedTextColor.RED)
            return
        }
        if (session.game.players.size != session.game.rule.playerCount) {
            val pc = session.game.rule.playerCount
            player.msg("需要${pc}名玩家才能开始 (当前 ${session.game.players.size}/$pc, 使用 /mahjong bot 添加机器人)", NamedTextColor.RED)
            return
        }
        if (!session.game.players.all { it.ready }) {
            player.msg("还有玩家未准备", NamedTextColor.RED)
            return
        }
        session.game.start()
    }

    private fun handleAddBot(player: Player) {
        if (!player.isOp) {
            player.msg("只有管理员可以添加机器人", NamedTextColor.RED)
            return
        }
        val session = manager.getSessionForPlayer(player.uniqueId.toString())
            ?: manager.getAllSessions().firstOrNull()
        if (session == null) {
            player.msg("没有可用的麻将桌", NamedTextColor.RED)
            return
        }
        if (session.game.status != GameStatus.WAITING) {
            player.msg("游戏已经开始", NamedTextColor.RED)
            return
        }
        if (session.game.players.size >= session.game.rule.playerCount) {
            player.msg("桌子已满", NamedTextColor.RED)
            return
        }
        val botNum = session.game.players.count { !it.isRealPlayer } + 1
        session.game.addBot("Bot$botNum")
        manager.updateTableDisplay(session)
        player.msg("已添加机器人 Bot$botNum (${session.game.players.size}/${session.game.rule.playerCount})", NamedTextColor.GREEN)
        manager.checkAutoStart(session)
    }

    private fun handleKick(player: Player, args: Array<out String>) {
        if (!player.isOp) {
            player.msg("只有管理员可以踢出玩家", NamedTextColor.RED)
            return
        }
        val session = manager.getSessionForPlayer(player.uniqueId.toString())
            ?: manager.getAllSessions().firstOrNull()
        if (session == null) {
            player.msg("没有可用的麻将桌", NamedTextColor.RED)
            return
        }
        val index = args.getOrNull(1)?.toIntOrNull()
        if (index == null || index !in session.game.players.indices) {
            player.msg("用法: /mahjong kick <座位号0-3>", NamedTextColor.RED)
            return
        }
        session.game.kick(index)
        player.msg("已踢出座位 $index", NamedTextColor.YELLOW)
    }

    private fun handleDestroy(player: Player, args: Array<out String>) {
        if (!player.isOp) {
            player.msg("只有管理员可以销毁麻将桌", NamedTextColor.RED)
            return
        }
        val humanId = args.drop(1).joinToString(" ")
        val session = if (humanId.isNotEmpty()) {
            manager.getSessionByHumanId(humanId)
        } else {
            manager.getAllSessions().firstOrNull()
        }
        if (session == null) {
            player.msg("找不到麻将桌: $humanId", NamedTextColor.RED)
            return
        }
        val name = session.humanId
        manager.destroyTable(session.tableId)
        player.msg("麻将桌 $name 已销毁", NamedTextColor.YELLOW)
    }

    private fun handleAction(player: Player, args: Array<out String>) {
        if (args.size < 2) return
        val session = manager.getSessionForPlayer(player.uniqueId.toString()) ?: return
        val mjPlayer = session.game.realPlayers.find { it.uuid == player.uniqueId.toString() } as? MahjongPlayer ?: return

        val behaviorName = args[1].uppercase()
        val behavior = try { MahjongGameBehavior.valueOf(behaviorName) } catch (_: Exception) { return }
        val data = if (args.size > 2) args.drop(2).joinToString(" ") else ""

        mjPlayer.resolveAction(behavior, data)
    }

    private fun handleList(player: Player) {
        val sessions = manager.getAllSessions()
        if (sessions.isEmpty()) {
            player.msg("没有活跃的麻将桌", NamedTextColor.YELLOW)
            return
        }
        player.msg("活跃的麻将桌:", NamedTextColor.GOLD)
        sessions.forEach { session ->
            val count = session.game.players.size
            val pc = session.game.rule.playerCount
            val status = session.game.status
            player.msg("  ${session.humanId} - $count/$pc 玩家 [$status]", NamedTextColor.AQUA)
        }
    }

    private fun handleInfo(player: Player) {
        val session = manager.getSessionForPlayer(player.uniqueId.toString())
        if (session == null) {
            player.msg("你不在任何麻将桌中", NamedTextColor.RED)
            return
        }
        player.msg("麻将桌 ${session.humanId}", NamedTextColor.GOLD)
        session.game.players.forEachIndexed { i, p ->
            val ready = if (p.ready) "✓" else "✗"
            val type = if (p.isRealPlayer) "玩家" else "机器人"
            player.msg("  $i. ${p.displayName} [$type] $ready", NamedTextColor.AQUA)
        }
        session.game.rule.toComponents().forEach { player.sendMessage(it) }
    }

    private fun sendHelp(player: Player) {
        player.msg("=== 麻将指令 ===", NamedTextColor.GOLD)
        player.msg("/mahjong create [one/east/twowind/three] - 创建麻将桌", NamedTextColor.YELLOW)
        player.msg("/mahjong join [id] - 加入麻将桌", NamedTextColor.YELLOW)
        player.msg("/mahjong leave - 离开麻将桌", NamedTextColor.YELLOW)
        player.msg("/mahjong ready/unready - 准备/取消准备", NamedTextColor.YELLOW)
        player.msg("/mahjong bot - 添加机器人", NamedTextColor.YELLOW)
        player.msg("/mahjong start - 开始游戏", NamedTextColor.YELLOW)
        player.msg("/mahjong destroy - 销毁麻将桌", NamedTextColor.YELLOW)
        player.msg("/mahjong info - 查看当前桌信息", NamedTextColor.YELLOW)
        player.msg("/mahjong list - 查看所有桌", NamedTextColor.YELLOW)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("create", "join", "leave", "ready", "unready", "start", "bot", "kick", "destroy", "info", "list")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].lowercase() == "create") {
            return listOf("one", "east", "twowind", "three")
                .filter { it.startsWith(args[1].lowercase()) }
        }
        if (args[0].lowercase() == "destroy") {
            val partial = args.drop(1).joinToString(" ")
            return manager.getAllHumanIds().filter { it.startsWith(partial) }
        }
        return emptyList()
    }

    private fun Player.msg(text: String, color: NamedTextColor) {
        sendMessage(Component.text("[麻将] ", NamedTextColor.GOLD).append(Component.text(text, color)))
    }
}
