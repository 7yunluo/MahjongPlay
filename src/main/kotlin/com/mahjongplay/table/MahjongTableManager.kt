package com.mahjongplay.table

import com.mahjongplay.MahjongPlayPlugin
import com.mahjongplay.display.BoardRenderer
import com.mahjongplay.game.GameStatus
import com.mahjongplay.game.MahjongBot
import com.mahjongplay.game.MahjongGame
import com.mahjongplay.interaction.GameRegistry
import com.mahjongplay.interaction.PaperGameBridge
import com.mahjongplay.model.MahjongRule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MahjongTableManager : GameRegistry {

    private val tables = ConcurrentHashMap<UUID, MahjongTableSession>()
    private val playerToTable = ConcurrentHashMap<String, UUID>()
    private val joinInteractionToTable = ConcurrentHashMap<UUID, UUID>()
    private val startInteractionToTable = ConcurrentHashMap<UUID, UUID>()
    private val readyInteractionToTable = ConcurrentHashMap<UUID, UUID>()
    private val countdownTasks = ConcurrentHashMap<UUID, Int>()
    private val countdownRemaining = ConcurrentHashMap<UUID, Int>()
    private val tableCounter = AtomicInteger(0)
    private val interruptedPlayers = ConcurrentHashMap.newKeySet<String>()
    private var dataFolder: File? = null
    private var loading = false

    fun createTable(center: Location, creatorUUID: String, creatorName: String, gameLength: MahjongRule.GameLength = MahjongRule.GameLength.TWO_WIND, playerCount: Int = 4, startingPoints: Int = 25000): MahjongTableSession {
        val game = MahjongGame(rule = MahjongRule(length = gameLength, playerCount = playerCount, startingPoints = startingPoints))
        val renderer = BoardRenderer(game, center)
        val bridge = PaperGameBridge(game, renderer, this)
        game.listener = bridge

        val modeText = if (gameLength == MahjongRule.GameLength.TWO_WIND && playerCount == 3) "三麻" else gameLength.displayText
        val existingNums = tables.values.map { it.humanId.substringAfterLast("]").removeSuffix("号桌").toIntOrNull() ?: 0 }.toSet()
        var tableNum = 1
        while (tableNum in existingNums) tableNum++
        val humanId = "[$modeText]${tableNum}号桌"

        val session = MahjongTableSession(
            tableId = game.tableId,
            game = game,
            renderer = renderer,
            bridge = bridge,
            center = center,
            table = MahjongTable(center, modeText, playerCount),
            humanId = humanId
        )

        tables[session.tableId] = session
        autoSave()

        return session
    }

    fun registerJoinInteraction(session: MahjongTableSession) {
        val interactionUUID = session.table.joinInteraction?.uniqueId ?: return
        joinInteractionToTable[interactionUUID] = session.tableId
        session.table.startInteraction?.uniqueId?.let { startInteractionToTable[it] = session.tableId }
        session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable[it] = session.tableId }
        updateTableDisplay(session)
    }

    fun joinTable(tableId: UUID, playerUUID: String, playerName: String): Boolean {
        if (playerToTable.containsKey(playerUUID)) return false
        val session = tables[tableId] ?: return false
        if (!session.game.join(playerUUID, playerName)) return false
        playerToTable[playerUUID] = tableId
        updateTableDisplay(session)
        return true
    }

    fun getTableByJoinInteraction(interactionUUID: UUID): MahjongTableSession? {
        val tableId = joinInteractionToTable[interactionUUID] ?: return null
        return tables[tableId]
    }

    fun getTableByStartInteraction(interactionUUID: UUID): MahjongTableSession? {
        val tableId = startInteractionToTable[interactionUUID] ?: return null
        return tables[tableId]
    }

    fun getTableByReadyInteraction(interactionUUID: UUID): MahjongTableSession? {
        val tableId = readyInteractionToTable[interactionUUID] ?: return null
        return tables[tableId]
    }

    fun startGame(session: MahjongTableSession): String? {
        if (session.game.status != GameStatus.WAITING) return "游戏已在进行中"
        if (session.game.players.isEmpty()) return "没有玩家"

        val unready = session.game.players.filter { it.isRealPlayer && !it.ready }
        if (unready.isNotEmpty()) return "还有玩家未准备: ${unready.joinToString { it.displayName }}"

        val pc = session.game.rule.playerCount
        while (session.game.players.size < pc) {
            val botNum = session.game.players.count { !it.isRealPlayer } + 1
            session.game.addBot("Bot$botNum")
        }

        session.table.hideActionButtons()
        startInteractionToTable.values.removeAll { it == session.tableId }
        readyInteractionToTable.values.removeAll { it == session.tableId }

        session.game.start()
        updateTableDisplay(session)
        return null
    }

    fun leaveTable(playerUUID: String): Boolean {
        val tableId = playerToTable[playerUUID] ?: return false
        val session = tables[tableId] ?: return false
        val wasPlaying = session.game.status == GameStatus.PLAYING

        session.bridge.hideBarForPlayer(playerUUID)
        session.game.leave(playerUUID)
        playerToTable.remove(playerUUID)

        if (wasPlaying) {
            session.game.players.forEach { playerToTable.remove(it.uuid) }
            session.game.players.clear()
        }

        if (session.game.realPlayers.isEmpty()) {
            session.game.players.removeAll { it is MahjongBot }
        }

        cancelCountdown(session.tableId)
        updateTableDisplay(session)
        return true
    }

    fun destroyTable(tableId: UUID) {
        val session = tables.remove(tableId) ?: return
        cancelCountdown(tableId)
        session.game.end()
        session.renderer.clearAllDisplays()
        session.bridge.cleanup()
        session.table.joinInteraction?.uniqueId?.let { joinInteractionToTable.remove(it) }
        session.table.startInteraction?.uniqueId?.let { startInteractionToTable.remove(it) }
        session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable.remove(it) }
        session.table.destroy()
        session.game.players.forEach { playerToTable.remove(it.uuid) }
        autoSave()
    }

    fun updateTableDisplay(session: MahjongTableSession) {
        val isWaiting = session.game.status == GameStatus.WAITING
        val playerInfo = session.game.players.map { it.displayName to it.ready }
        session.table.updateJoinDisplay(
            playerCount = session.game.players.size,
            maxPlayers = session.game.rule.playerCount,
            waiting = isWaiting,
            playerInfo = playerInfo
        )
        session.table.startInteraction?.uniqueId?.let { startInteractionToTable[it] = session.tableId }
        session.table.readyInteraction?.uniqueId?.let { readyInteractionToTable[it] = session.tableId }
    }

    fun checkAutoStart(session: MahjongTableSession) {
        cancelCountdown(session.tableId)

        val pc = session.game.rule.playerCount
        if (session.game.status != GameStatus.WAITING) return
        if (session.game.players.size != pc) return
        if (!session.game.players.all { it.ready }) return

        countdownRemaining[session.tableId] = 3
        val taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MahjongPlayPlugin.instance, {
            val remaining = countdownRemaining[session.tableId] ?: return@scheduleSyncRepeatingTask
            if (remaining > 0) {
                session.table.showCountdown(remaining)
                session.game.players.forEach { mjp ->
                    Bukkit.getPlayer(UUID.fromString(mjp.uuid))?.sendActionBar(
                        Component.text("游戏将在 ${remaining} 秒后开始...", NamedTextColor.GOLD)
                    )
                }
                countdownRemaining[session.tableId] = remaining - 1
            } else {
                cancelCountdown(session.tableId)
                if (session.game.status == GameStatus.WAITING
                    && session.game.players.size == pc
                    && session.game.players.all { it.ready }
                ) {
                    session.game.start()
                    updateTableDisplay(session)
                }
            }
        }, 0L, 20L)
        countdownTasks[session.tableId] = taskId
    }

    fun cancelCountdown(tableId: UUID) {
        countdownTasks.remove(tableId)?.let { Bukkit.getScheduler().cancelTask(it) }
        countdownRemaining.remove(tableId)
    }

    fun getSessionForPlayer(playerUUID: String): MahjongTableSession? {
        val tableId = playerToTable[playerUUID] ?: return null
        return tables[tableId]
    }

    fun getSession(tableId: UUID): MahjongTableSession? = tables[tableId]

    fun getAllSessions(): Collection<MahjongTableSession> = tables.values

    fun getAllHumanIds(): List<String> = tables.values.map { it.humanId }

    fun getSessionByHumanId(humanId: String): MahjongTableSession? =
        tables.values.find { it.humanId == humanId }

    fun shutdown() {
        tables.values.forEach { session ->
            if (session.game.status == GameStatus.PLAYING) {
                session.game.end()
            }
            session.renderer.clearAllDisplays()
            session.bridge.cleanup()
            session.table.removeEntities()
            session.game.players.forEach { playerToTable.remove(it.uuid) }
        }
        tables.clear()
        joinInteractionToTable.clear()
        startInteractionToTable.clear()
        readyInteractionToTable.clear()
    }

    fun saveTables(dataFolder: File) {
        this.dataFolder = dataFolder
        val file = File(dataFolder, "tables.yml")
        val config = YamlConfiguration()
        val tableList = tables.values.map { session ->
            val map = mutableMapOf<String, Any>(
                "world" to session.center.world.name,
                "x" to session.center.blockX,
                "y" to session.center.blockY,
                "z" to session.center.blockZ,
                "gameLength" to session.game.rule.length.name,
                "playerCount" to session.game.rule.playerCount,
                "startingPoints" to session.game.rule.startingPoints
            )
            if (session.game.status == GameStatus.PLAYING) {
                map["playingPlayers"] = session.game.realPlayers.map { it.uuid }
            }
            map
        }
        config.set("tables", tableList)
        config.save(file)
    }

    private fun autoSave() {
        if (loading) return
        val folder = dataFolder ?: return
        saveTables(folder)
    }

    fun loadTables(dataFolder: File) {
        this.dataFolder = dataFolder
        loading = true
        val file = File(dataFolder, "tables.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        val tableList = config.getMapList("tables")
        tableList.forEach { map ->
            val worldName = map["world"] as? String ?: return@forEach
            val world = Bukkit.getWorld(worldName) ?: return@forEach
            val x = (map["x"] as? Number)?.toInt() ?: return@forEach
            val y = (map["y"] as? Number)?.toInt() ?: return@forEach
            val z = (map["z"] as? Number)?.toInt() ?: return@forEach
            val gameLengthName = map["gameLength"] as? String ?: "TWO_WIND"
            val playerCount = (map["playerCount"] as? Number)?.toInt() ?: 4
            val startingPoints = (map["startingPoints"] as? Number)?.toInt() ?: 25000
            val gameLength = try { MahjongRule.GameLength.valueOf(gameLengthName) } catch (_: Exception) { MahjongRule.GameLength.TWO_WIND }

            @Suppress("UNCHECKED_CAST")
            val playingPlayers = map["playingPlayers"] as? List<String> ?: emptyList()
            playingPlayers.forEach { interruptedPlayers.add(it) }

            val center = Location(world, x + 0.5, y.toDouble(), z + 0.5)
            val session = createTable(center, "", "", gameLength, playerCount, startingPoints)
            session.table.spawn()
            registerJoinInteraction(session)
        }
        loading = false
    }

    fun isProtectedBlock(loc: org.bukkit.Location): Boolean {
        return tables.values.any { it.table.isProtectedBlock(loc) }
    }

    fun notifyIfInterrupted(playerUUID: String, playerBukkit: org.bukkit.entity.Player) {
        if (interruptedPlayers.remove(playerUUID)) {
            Bukkit.getScheduler().runTaskLater(MahjongPlayPlugin.instance, Runnable {
                playerBukkit.sendMessage(
                    Component.text("[麻将] ", NamedTextColor.GOLD)
                        .append(Component.text("你之前的牌局因服务器重启而中断，非常抱歉！", NamedTextColor.YELLOW))
                )
            }, 40L)
        }
    }

    override fun getGameForPlayer(uuid: String): MahjongGame? =
        getSessionForPlayer(uuid)?.game

    override fun getRenderer(game: MahjongGame): BoardRenderer? =
        tables[game.tableId]?.renderer
}

data class MahjongTableSession(
    val tableId: UUID,
    val game: MahjongGame,
    val renderer: BoardRenderer,
    val bridge: PaperGameBridge,
    val center: Location,
    val table: MahjongTable,
    val humanId: String
)
