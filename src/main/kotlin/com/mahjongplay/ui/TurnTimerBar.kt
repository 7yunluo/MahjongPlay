package com.mahjongplay.ui

import com.mahjongplay.MahjongPlayPlugin
import com.mahjongplay.game.MahjongGame
import com.mahjongplay.game.MahjongPlayerBase
import com.mahjongplay.model.MahjongGameBehavior
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import java.util.UUID

class TurnTimerBar(private val game: MahjongGame) {

    private var bar: BossBar? = null
    private var timerTaskId: Int = -1
    private var startTimeMs: Long = 0
    private var durationMs: Long = 0
    private var activePlayerUUID: String? = null
    private var activeActions: List<MahjongGameBehavior> = emptyList()
    private val shownPlayerUUIDs = mutableSetOf<UUID>()

    private val windNames: List<String>
        get() = if (game.rule.playerCount == 3) listOf("东", "南", "西") else listOf("东", "南", "西", "北")

    fun show() {
        if (bar != null) return
        val b = BossBar.bossBar(buildTitle(0), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
        bar = b
        showToAll(b)
        startIdleUpdater()
    }

    fun hide() {
        cancelTimer()
        bar?.let { b ->
            shownPlayerUUIDs.forEach { uuid ->
                Bukkit.getPlayer(uuid)?.hideBossBar(b)
            }
            shownPlayerUUIDs.clear()
        }
        bar = null
    }

    fun startAction(player: MahjongPlayerBase, actions: List<MahjongGameBehavior>, totalSeconds: Int) {
        cancelTimer()
        activePlayerUUID = player.uuid
        activeActions = actions
        startTimeMs = System.currentTimeMillis()
        durationMs = totalSeconds * 1000L

        val b = bar ?: return
        b.progress(1.0f)
        b.color(BossBar.Color.GREEN)
        b.name(buildTitle(totalSeconds))

        timerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MahjongPlayPlugin.instance, {
            val elapsed = System.currentTimeMillis() - startTimeMs
            val remaining = (durationMs - elapsed).coerceAtLeast(0)
            val progress = (remaining.toFloat() / durationMs).coerceIn(0f, 1f)
            val remainSec = (remaining / 1000).toInt()

            b.progress(progress)
            b.color(when {
                progress > 0.5f -> BossBar.Color.GREEN
                progress > 0.25f -> BossBar.Color.YELLOW
                else -> BossBar.Color.RED
            })
            b.name(buildTitle(remainSec))

            if (remaining <= 0) endAction()
        }, 0L, 2L)
    }

    fun endAction() {
        cancelTimer()
        activePlayerUUID = null
        activeActions = emptyList()
        bar?.let {
            it.progress(1.0f)
            it.color(BossBar.Color.GREEN)
            it.name(buildTitle(0))
        }
    }

    private fun buildTitle(remainSec: Int): Component {
        val pc = game.rule.playerCount
        val seatOrder = if (game.seat.isEmpty()) emptyList()
        else List(pc) { game.seat[(game.round.round + it) % pc] }

        val round = game.round
        var title = Component.text("${round.displayName()} ", NamedTextColor.GOLD)
            .append(Component.text("牌山${game.wallSize} ", NamedTextColor.GREEN))
            .append(Component.text("| ", NamedTextColor.DARK_GRAY))

        seatOrder.forEachIndexed { idx, player ->
            val wind = windNames.getOrElse(idx) { "?" }
            val isActive = player.uuid == activePlayerUUID
            val name = player.displayName

            if (isActive) {
                val secColor = if (remainSec <= 5) NamedTextColor.RED else NamedTextColor.WHITE
                title = title
                    .append(Component.text("$wind(", NamedTextColor.YELLOW))
                    .append(Component.text(name, NamedTextColor.AQUA))
                    .append(Component.text(") ", NamedTextColor.YELLOW))
                    .append(Component.text("${remainSec}s", secColor))
            } else {
                title = title
                    .append(Component.text("$wind(", NamedTextColor.GRAY))
                    .append(Component.text(name, NamedTextColor.GRAY))
                    .append(Component.text(")", NamedTextColor.GRAY))
            }

            if (idx < seatOrder.size - 1) {
                title = title.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            }
        }

        return title
    }

    private fun showToAll(b: BossBar) {
        game.players.forEach { mjp ->
            val uuid = UUID.fromString(mjp.uuid)
            Bukkit.getPlayer(uuid)?.showBossBar(b)
            shownPlayerUUIDs.add(uuid)
        }
    }

    private fun cancelTimer() {
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId)
            timerTaskId = -1
        }
    }

    private var idleTaskId: Int = -1

    private fun startIdleUpdater() {
        idleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MahjongPlayPlugin.instance, {
            if (activePlayerUUID == null) {
                bar?.name(buildTitle(0))
            }
        }, 0L, 20L)
    }

    fun hideForPlayer(playerUUID: String) {
        val uuid = UUID.fromString(playerUUID)
        bar?.let { b -> Bukkit.getPlayer(uuid)?.hideBossBar(b) }
        shownPlayerUUIDs.remove(uuid)
    }

    fun cleanup() {
        cancelTimer()
        if (idleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(idleTaskId)
            idleTaskId = -1
        }
        hide()
    }
}
