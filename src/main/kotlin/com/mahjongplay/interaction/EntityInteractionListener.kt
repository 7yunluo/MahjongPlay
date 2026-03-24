package com.mahjongplay.interaction

import com.mahjongplay.game.GameStatus
import com.mahjongplay.game.MahjongGame
import com.mahjongplay.model.MahjongGameBehavior
import com.mahjongplay.game.MahjongPlayer
import com.mahjongplay.table.MahjongTableManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent

class EntityInteractionListener(
    private val gameManager: MahjongTableManager
) : Listener {

    @EventHandler
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val clickedEntity = event.rightClicked
        if (clickedEntity !is Interaction) return

        val player = event.player
        val playerUUID = player.uniqueId.toString()

        val joinSession = gameManager.getTableByJoinInteraction(clickedEntity.uniqueId)
        if (joinSession != null) {
            event.isCancelled = true
            if (joinSession.game.status != GameStatus.WAITING) {
                player.sendMessage(Component.text("[麻将] 游戏正在进行中", NamedTextColor.RED))
                return
            }

            val existingSession = gameManager.getSessionForPlayer(playerUUID)
            if (existingSession != null && existingSession.tableId == joinSession.tableId) {
                gameManager.leaveTable(playerUUID)
                player.sendMessage(Component.text("[麻将] 已退出麻将桌", NamedTextColor.YELLOW))
                return
            }

            if (existingSession != null) {
                player.sendMessage(Component.text("[麻将] 你已经在另一个麻将桌中了", NamedTextColor.RED))
                return
            }
            if (gameManager.joinTable(joinSession.tableId, playerUUID, player.name)) {
                player.sendMessage(Component.text("[麻将] 已加入麻将桌!", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("[麻将] 无法加入 (桌子已满)", NamedTextColor.RED))
            }
            return
        }

        val readySession = gameManager.getTableByReadyInteraction(clickedEntity.uniqueId)
        if (readySession != null) {
            event.isCancelled = true
            if (readySession.game.status != GameStatus.WAITING) return

            val mjPlayer = readySession.game.players.find { it.uuid == playerUUID }
            if (mjPlayer == null) {
                player.sendMessage(Component.text("[麻将] 你不在这个麻将桌中", NamedTextColor.RED))
                return
            }
            val newReady = !mjPlayer.ready
            readySession.game.readyOrNot(playerUUID, newReady)
            gameManager.updateTableDisplay(readySession)
            player.sendMessage(Component.text("[麻将] ${if (newReady) "已准备 ✓" else "取消准备 ✗"}", if (newReady) NamedTextColor.GREEN else NamedTextColor.YELLOW))
            return
        }

        val startSession = gameManager.getTableByStartInteraction(clickedEntity.uniqueId)
        if (startSession != null) {
            event.isCancelled = true
            if (startSession.game.status != GameStatus.WAITING) return

            val mjPlayer = startSession.game.players.find { it.uuid == playerUUID }
            if (mjPlayer == null) {
                player.sendMessage(Component.text("[麻将] 你不在这个麻将桌中", NamedTextColor.RED))
                return
            }
            if (!mjPlayer.ready) {
                player.sendMessage(Component.text("[麻将] 请先准备", NamedTextColor.RED))
                return
            }

            val error = gameManager.startGame(startSession)
            if (error != null) {
                player.sendMessage(Component.text("[麻将] $error", NamedTextColor.RED))
            }
            return
        }

        val game = gameManager.getGameForPlayer(playerUUID) ?: return
        val mjPlayer = game.realPlayers.find { it.uuid == playerUUID } as? MahjongPlayer ?: return
        val renderer = gameManager.getRenderer(game) ?: return

        val actionDisplay = renderer.getActionByInteraction(clickedEntity.uniqueId)
        if (actionDisplay != null && actionDisplay.ownerUUID == playerUUID) {
            event.isCancelled = true
            if (actionDisplay.subOptions != null && actionDisplay.subOptions.isNotEmpty()) {
                renderer.expandSubMenu(playerUUID, actionDisplay.subOptions)
            } else {
                mjPlayer.resolveAction(actionDisplay.behavior, actionDisplay.data)
            }
            return
        }

        val pending = mjPlayer.pendingAction ?: return
        if (MahjongGameBehavior.DISCARD !in pending.behaviors) return

        val ownerDisplays = renderer.handOwnerDisplays[mjPlayer.uuid] ?: return

        val clickedIndex = ownerDisplays.indexOfFirst {
            it.interactionEntity?.uniqueId == clickedEntity.uniqueId
        }
        if (clickedIndex < 0) return

        val tile = mjPlayer.hands.getOrNull(clickedIndex) ?: return
        event.isCancelled = true

        val confirmed = renderer.selectTileForDiscard(mjPlayer.uuid, clickedIndex)
        if (confirmed) {
            mjPlayer.resolveAction(MahjongGameBehavior.DISCARD, "${tile.code}")
        }
    }
}

interface GameRegistry {
    fun getGameForPlayer(uuid: String): MahjongGame?
    fun getRenderer(game: MahjongGame): com.mahjongplay.display.BoardRenderer?
}
