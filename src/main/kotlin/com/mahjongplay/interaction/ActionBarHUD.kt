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

            val previewMachi = mjPlayer.previewMachiTiles
            if (previewMachi.isNotEmpty()) {
                val machiStr = previewMachi
                    .distinctBy { it.mahjong4jTile }
                    .filterNot { it.isRed }
                    .joinToString(",") { it.displayName }
                bar = bar.append(Component.text("|听:$machiStr", NamedTextColor.LIGHT_PURPLE))
            }

            player.sendActionBar(bar)
        }
    }

    private fun seatWindOf(game: MahjongGame, player: MahjongPlayerBase): String {
        val pc = game.rule.playerCount
        val seatOrder = List(pc) { game.seat[(game.round.round + it) % pc] }
        val windNames = if (pc == 3) listOf("东(庄)", "南", "西") else listOf("东(庄)", "南", "西", "北")
        val idx = seatOrder.indexOf(player)
        return if (idx in windNames.indices) windNames[idx] else "?"
    }
}
