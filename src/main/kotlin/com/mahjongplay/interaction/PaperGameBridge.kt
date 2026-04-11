package com.mahjongplay.interaction

import com.mahjongplay.MahjongPlayPlugin
import com.mahjongplay.display.BoardRenderer
import com.mahjongplay.game.*
import com.mahjongplay.model.*
import com.mahjongplay.ui.TurnTimerBar
import com.mahjongplay.util.YakuNameChinese
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID

class PaperGameBridge(
    val game: MahjongGame,
    val renderer: BoardRenderer,
    val tableManager: com.mahjongplay.table.MahjongTableManager,
) : GameEventListener, PendingActionListener {

    private var hudTaskId: Int = -1
    private val turnTimerBar = TurnTimerBar(game)

    override fun onGameStart(game: MahjongGame) {
        renderer.onGameStart(game)
        game.realPlayers.forEach {
            it.gameId = game.tableId
            it.pendingActionListener = this
        }
        tableManager.getSession(game.tableId)?.let { tableManager.updateTableDisplay(it) }
        broadcast(Component.text("[麻将] 游戏开始!", NamedTextColor.GOLD))
        startHudUpdates()
        turnTimerBar.show()
    }

    override fun onRoundStart(game: MahjongGame, round: MahjongRound) {
        renderer.onRoundStart(game, round)
        val title = Title.title(
            Component.text(round.displayName(), NamedTextColor.GOLD),
            Component.text("本场${round.honba}", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
        )
        forEachPlayer { it.showTitle(title) }
    }

    override fun onTileDrawn(player: MahjongPlayerBase, tile: MahjongTile) {
        renderer.onTileDrawn(player, tile)
    }

    override fun onTileDiscarded(player: MahjongPlayerBase, tile: MahjongTile) {
        renderer.onTileDiscarded(player, tile)
        updateHud()
    }

    override fun onHandsUpdated(player: MahjongPlayerBase) {
        renderer.onHandsUpdated(player)
        updateHud()
    }

    private val quickTitleTimes = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(200))

    private fun showEventTitle(main: Component, subtitle: Component) {
        val title = Title.title(main, subtitle, quickTitleTimes)
        forEachPlayer { it.showTitle(title) }
    }

    override fun onChii(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        renderer.onChii(player, claimedTile, from)
        showEventTitle(
            Component.text("吃!", NamedTextColor.GREEN),
            Component.text(player.displayName, NamedTextColor.AQUA)
        )
    }

    override fun onPon(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        renderer.onPon(player, claimedTile, from)
        showEventTitle(
            Component.text("碰!", NamedTextColor.BLUE),
            Component.text(player.displayName, NamedTextColor.AQUA)
        )
    }

    override fun onKan(player: MahjongPlayerBase, tile: MahjongTile, kanType: String, from: MahjongPlayerBase?) {
        renderer.onKan(player, tile, kanType, from)
        showEventTitle(
            Component.text("杠!", NamedTextColor.DARK_AQUA),
            Component.text(player.displayName, NamedTextColor.AQUA)
        )
    }

    override fun onRiichi(player: MahjongPlayerBase, tile: MahjongTile) {
        renderer.onRiichi(player, tile)
        showEventTitle(
            Component.text("立直!", NamedTextColor.LIGHT_PURPLE),
            Component.text(player.displayName, NamedTextColor.AQUA)
        )
    }

    override fun onTsumo(player: MahjongPlayerBase, tile: MahjongTile, settlement: YakuSettlement) {
        Bukkit.getScheduler().runTask(MahjongPlayPlugin.instance, Runnable {
            renderer.revealHands(player)
        })
        showEventTitle(
            Component.text("自摸!", NamedTextColor.GOLD),
            Component.text(player.displayName, NamedTextColor.AQUA)
        )
        sendYakuSummary(settlement)
    }

    override fun onRon(winners: List<MahjongPlayerBase>, loser: MahjongPlayerBase, tile: MahjongTile, settlements: List<YakuSettlement>) {
        Bukkit.getScheduler().runTask(MahjongPlayPlugin.instance, Runnable {
            winners.forEach { renderer.revealHands(it) }
        })
        val names = winners.joinToString(", ") { it.displayName }
        showEventTitle(
            Component.text("荣和!", NamedTextColor.RED),
            Component.text(names, NamedTextColor.AQUA)
        )
        settlements.forEach { sendYakuSummary(it) }
    }

    override fun onDraw(draw: ExhaustiveDraw, settlement: ScoreSettlement) {
        if (draw == ExhaustiveDraw.NORMAL) {
            Bukkit.getScheduler().runTask(MahjongPlayPlugin.instance, Runnable {
                game.players.filter { it.isTenpai }.forEach { renderer.revealHands(it) }
            })
        }
        showEventTitle(
            draw.toText().color(NamedTextColor.YELLOW),
            Component.text("流局", NamedTextColor.GRAY)
        )
    }

    override fun onScoreSettlement(settlement: ScoreSettlement) {
        settlement.rankedScoreList.forEachIndexed { index, ranked ->
            val line = Component.text("  ${index + 1}. ", NamedTextColor.YELLOW)
                .append(Component.text(ranked.scoreItem.displayName, NamedTextColor.AQUA))
                .append(Component.text("  ${ranked.scoreTotal}点", NamedTextColor.WHITE))
                .append(Component.text(" (${ranked.scoreChangeText})", NamedTextColor.GRAY))
            broadcast(line)
        }
    }

    override fun onGameEnd(game: MahjongGame, scoreList: List<ScoreItem>) {
        Bukkit.getScheduler().runTask(MahjongPlayPlugin.instance, Runnable {
            renderer.onGameEnd(game, scoreList)
            stopHudUpdates()
            turnTimerBar.cleanup()
            tableManager.getSession(game.tableId)?.let {
                tableManager.updateTableDisplay(it)
                it.table.showActionButtons()
                tableManager.registerJoinInteraction(it)
            }
            broadcast(Component.text("[麻将] 游戏结束!", NamedTextColor.GOLD))
            val sorted = scoreList.sortedByDescending { it.scoreOrigin }
            sorted.forEachIndexed { index, item ->
                broadcast(Component.text("  ${index + 1}. ${item.displayName}  ${item.scoreOrigin}点", NamedTextColor.YELLOW))
            }
        })
    }

    private fun sendYakuSummary(settlement: YakuSettlement) {
        val yakuNames = buildList {
            addAll(settlement.yakuList.map { YakuNameChinese.getName(it) })
            addAll(settlement.yakumanList.map { YakuNameChinese.getName(it) })
            addAll(settlement.doubleYakumanList.map { PlainTextComponentSerializer.plainText().serialize(it.toText()) })
            if (settlement.nagashiMangan) add("流局满贯")
            if (settlement.nukiDoraCount > 0) add("拔北×${settlement.nukiDoraCount}")
        }
        if (yakuNames.isEmpty()) return

        val scoreLine = buildString {
            val isYakuman = settlement.yakumanList.isNotEmpty() || settlement.doubleYakumanList.isNotEmpty()
            if (!isYakuman && !settlement.nagashiMangan) {
                append(" ${settlement.han}翻${settlement.fu}符")
            }
            append(" ${settlement.score}点")
            val alias = when {
                settlement.nagashiMangan -> "满贯"
                isYakuman -> {
                    val rate = (settlement.yakumanList.size + settlement.doubleYakumanList.size * 2).coerceAtMost(6)
                    when (rate) {
                        1 -> "役满"; 2 -> "二倍役满"; 3 -> "三倍役满"
                        4 -> "四倍役满"; 5 -> "五倍役满"; else -> "六倍役满"
                    }
                }
                settlement.han >= 13 -> "累计役满"
                settlement.han >= 11 -> "三倍满"
                settlement.han >= 8 -> "倍满"
                settlement.han >= 6 -> "跳满"
                settlement.han >= 5 || (settlement.fu >= 40 && settlement.han == 4) || (settlement.fu >= 70 && settlement.han == 3) -> "满贯"
                else -> null
            }
            if (alias != null) append("  !!$alias!!")
        }

        broadcast(Component.text("  役: ${yakuNames.joinToString(", ")}", NamedTextColor.GREEN)
            .append(Component.text(scoreLine, NamedTextColor.YELLOW)))
    }

    private fun startHudUpdates() {
        hudTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MahjongPlayPlugin.instance, { updateHud() }, 0L, 20L)
    }

    private fun stopHudUpdates() {
        if (hudTaskId != -1) { Bukkit.getScheduler().cancelTask(hudTaskId); hudTaskId = -1 }
    }

    private fun updateHud() {
        ActionBarHUD.sendUpdate(game)
    }

    private fun broadcast(msg: Component) {
        forEachPlayer { it.sendMessage(msg) }
    }

    private fun forEachPlayer(action: (Player) -> Unit) {
        game.players.forEach { mjp ->
            val p = Bukkit.getPlayer(UUID.fromString(mjp.uuid))
            if (p != null) action(p)
        }
    }

    fun cleanup() {
        stopHudUpdates()
        turnTimerBar.cleanup()
    }

    override fun onPendingActionStart(player: MahjongPlayer, behaviors: List<MahjongGameBehavior>, timeoutSeconds: Int) {
        Bukkit.getScheduler().runTask(MahjongPlayPlugin.instance, Runnable {
            turnTimerBar.startAction(player, behaviors, timeoutSeconds)
            renderer.spawnActionOptions(player.uuid, player.actionOptions)
        })
    }

    override fun onPendingActionEnd(player: MahjongPlayer) {
        Bukkit.getScheduler().runTask(MahjongPlayPlugin.instance, Runnable {
            turnTimerBar.endAction()
            if (player.riichiSelectionMode) {
                player.riichiSelectionMode = false
                renderer.exitRiichiMode(player.uuid)
            }
            renderer.clearActionOptions(player.uuid)
        })
    }
}
