package com.mahjongplay.interaction

import com.mahjongplay.game.MahjongGame
import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.game.MahjongPlayerBase
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.model.MahjongRound
import com.mahjongplay.model.MahjongTile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

object ActionBarHUD {

    fun sendUpdate(game: MahjongGame) {
        val round = game.round
        val wallSize = game.wallSize

        game.realPlayers.forEach { mjPlayer ->
            val player = Bukkit.getPlayer(UUID.fromString(mjPlayer.uuid)) ?: return@forEach

            val riichiOverride = (mjPlayer as? MahjongPlayer)?.riichiActionBarOverride
            if (riichiOverride != null) {
                player.sendActionBar(riichiOverride)
                return@forEach
            }

            val seatWind = seatWindOf(game, mjPlayer)
            val doraStr = game.doraIndicators.joinToString(",") { it.doraFromIndicator(game.rule.isSanma).displayName }

            var bar = Component.text("${round.displayName()}", NamedTextColor.GOLD)
                .append(Component.text("|本场${round.honba}", NamedTextColor.YELLOW))
                .append(Component.text("|$seatWind", NamedTextColor.AQUA))
                .append(Component.text("|牌山$wallSize", NamedTextColor.GREEN))
                .append(Component.text("|${mjPlayer.points}点", NamedTextColor.WHITE))
                .append(Component.text("|宝牌:$doraStr", NamedTextColor.RED))

            if (mjPlayer.isTenpai) {
                val machiStr = mjPlayer.machiTiles.joinToString(",") { it.displayName }
                bar = bar.append(Component.text("|听:$machiStr", NamedTextColor.LIGHT_PURPLE))
            }

            val pending = (mjPlayer as? MahjongPlayer)?.pendingAction
            if (pending != null) {
                val hint = actionHint(pending.behaviors)
                bar = bar.append(Component.text(" ⚡$hint", NamedTextColor.LIGHT_PURPLE)
                    .decorate(TextDecoration.BOLD))
            }

            player.sendActionBar(bar)
        }
    }

    private fun actionHint(behaviors: List<MahjongGameBehavior>): String {
        val actions = behaviors.filter { it != MahjongGameBehavior.SKIP }
        if (actions.isEmpty()) return ""
        val hasDiscard = MahjongGameBehavior.DISCARD in actions
        if (hasDiscard) return "请出牌(点击手牌)"
        val names = actions.joinToString("/") { PlainTextComponentSerializer.plainText().serialize(it.toText()) }
        return "可${names}! 请在聊天栏点击操作"
    }

    private fun seatWindOf(game: MahjongGame, player: MahjongPlayerBase): String {
        val pc = game.rule.playerCount
        val seatOrder = List(pc) { game.seat[(game.round.round + it) % pc] }
        val windNames = if (pc == 3) listOf("东(庄)", "南", "西") else listOf("东(庄)", "南", "西", "北")
        val idx = seatOrder.indexOf(player)
        return if (idx in windNames.indices) windNames[idx] else "?"
    }
}
