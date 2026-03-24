package com.mahjongcraft.display

import com.mahjongcraft.MahjongCraftPlugin
import com.mahjongcraft.display.TileConstants.DEPTH
import com.mahjongcraft.display.TileConstants.HEIGHT
import com.mahjongcraft.display.TileConstants.PADDING
import com.mahjongcraft.display.TileConstants.WIDTH
import com.mahjongcraft.game.*
import com.mahjongcraft.model.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActionDisplay(
    val textDisplay: TextDisplay,
    val interaction: org.bukkit.entity.Interaction,
    val behavior: MahjongGameBehavior,
    val data: String,
    val ownerUUID: String,
    val subOptions: List<ActionDisplayOption>? = null
)

class BoardRenderer(
    val game: MahjongGame,
    val tableCenter: Location,
) : GameEventListener {

    val handDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    val handOwnerDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val discardDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val fuuroDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val nukiDoraDisplays = ConcurrentHashMap<String, MutableList<MahjongTileDisplay>>()
    private val botNameDisplays = mutableListOf<TextDisplay>()
    private var floatingCenterDisplay: MahjongTileDisplay? = null
    private val selectedTileIndices = ConcurrentHashMap<String, Int>()
    private val actionDisplays = ConcurrentHashMap<String, MutableList<ActionDisplay>>()
    private val highlightedDiscards = mutableListOf<MahjongTileDisplay>()

    companion object {
        private const val RAISE_OFFSET = 0.05
        private const val ACTION_BUTTON_SPACING = 0.55
    }

    private val world: World get() = tableCenter.world

    private val surfaceY: Double get() = tableCenter.blockY + 1.0 + 1.0 / 16.0
    private val standingTileY: Double get() = surfaceY + HEIGHT / 2.0
    private val flatTileY: Double get() = surfaceY + DEPTH / 2.0

    // Face points toward center so player behind the tile sees it
    private fun physicalSeatIndex(seatIndex: Int): Int {
        if (game.rule.playerCount == 3) {
            return when (seatIndex) {
                0 -> 0  // East
                1 -> 3  // South
                2 -> 2  // West
                else -> seatIndex
            }
        }
        return seatIndex
    }

    private fun seatYaw(seatIndex: Int): Float = when (physicalSeatIndex(seatIndex)) {
        0 -> 90f    // East: face toward -X (center)
        3 -> 0f     // South: face toward -Z (center)
        2 -> -90f   // West: face toward +X (center)
        else -> 180f // North: face toward +Z (center)
    }

    private fun seatDirection(seatIndex: Int): DoubleArray = when (physicalSeatIndex(seatIndex)) {
        0 -> doubleArrayOf(1.0, 0.0)
        3 -> doubleArrayOf(0.0, 1.0)
        2 -> doubleArrayOf(-1.0, 0.0)
        else -> doubleArrayOf(0.0, -1.0)
    }

    // Left-to-right direction from each player's perspective
    private fun seatPerpendicular(seatIndex: Int): DoubleArray = when (physicalSeatIndex(seatIndex)) {
        0 -> doubleArrayOf(0.0, 1.0)    // East: +Z to -Z (south to north)
        3 -> doubleArrayOf(-1.0, 0.0)   // South: -X to +X (west to east)
        2 -> doubleArrayOf(0.0, -1.0)   // West: -Z to +Z (north to south)
        else -> doubleArrayOf(1.0, 0.0)  // North: +X to -X (east to west)
    }

    override fun onRoundStart(game: MahjongGame, round: MahjongRound) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            clearAllDisplays()
            spawnBotNameTags()
        })
    }

    override fun onHandsUpdated(player: MahjongPlayerBase) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            renderHands(player)
            if (player.nukiDoraTiles.isNotEmpty()) renderNukiDora(player)
        })
    }

    override fun onTileDiscarded(player: MahjongPlayerBase, tile: MahjongTile) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            renderDiscards(player)
            spawnFloatingCenterTile(tile)
        })
    }

    override fun onPon(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            renderFuuro(player)
            renderDiscards(from)
        })
    }

    override fun onChii(player: MahjongPlayerBase, claimedTile: MahjongTile, from: MahjongPlayerBase) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            renderFuuro(player)
            renderDiscards(from)
        })
    }

    override fun onKan(player: MahjongPlayerBase, tile: MahjongTile, kanType: String) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            renderFuuro(player)
        })
    }

    override fun onGameEnd(game: MahjongGame, scoreList: List<ScoreItem>) {
        Bukkit.getScheduler().runTask(MahjongCraftPlugin.instance, Runnable {
            clearAllDisplays()
        })
    }

    fun renderHands(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        selectedTileIndices.remove(player.uuid)
        unhighlightDiscards()

        val existing = handDisplays.getOrPut(player.uuid) { mutableListOf() }
        existing.forEach { it.remove() }
        existing.clear()

        val ownerExisting = handOwnerDisplays.getOrPut(player.uuid) { mutableListOf() }
        ownerExisting.forEach { it.remove() }
        ownerExisting.clear()

        val tileCount = player.hands.size
        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val dirOffset = 0.85 + DEPTH + HEIGHT
        val totalWidth = tileCount * WIDTH + (tileCount - 1) * PADDING
        val startOffset = totalWidth / 2.0
        val yaw = seatYaw(seatIndex)

        player.hands.forEachIndexed { index, tile ->
            val isLast = index == tileCount - 1 && tileCount == 14
            val tileOffset = index * (WIDTH + PADDING) + if (isLast) PADDING * 15.0 else 0.0

            val x = tableCenter.x + dir[0] * dirOffset + perp[0] * (startOffset - tileOffset)
            val z = tableCenter.z + dir[1] * dirOffset + perp[1] * (startOffset - tileOffset)
            val loc = Location(world, x, standingTileY, z)

            val backDisplay = MahjongTileDisplay(loc, MahjongTile.UNKNOWN, TileFace.STANDING, yaw)
            backDisplay.spawn()
            existing += backDisplay

            val isRealPlayer = player.isRealPlayer
            val ownerDisplay = MahjongTileDisplay(loc.clone(), tile, TileFace.STANDING, yaw, interactive = isRealPlayer)
            ownerDisplay.spawn()
            ownerExisting += ownerDisplay
        }

        updateVisibility(player)
    }

    fun selectTileForDiscard(playerUUID: String, clickedIndex: Int): Boolean {
        val currentSelected = selectedTileIndices[playerUUID]

        if (currentSelected == clickedIndex) {
            selectedTileIndices.remove(playerUUID)
            lowerTileAt(playerUUID, clickedIndex)
            unhighlightDiscards()
            return true
        }

        if (currentSelected != null) {
            lowerTileAt(playerUUID, currentSelected)
        }

        raiseTileAt(playerUUID, clickedIndex)
        selectedTileIndices[playerUUID] = clickedIndex

        val player = game.seat.find { it.uuid == playerUUID }
        val tile = player?.hands?.getOrNull(clickedIndex)
        if (tile != null) {
            highlightMatchingDiscards(tile)
        }

        return false
    }

    private fun highlightMatchingDiscards(tile: MahjongTile) {
        unhighlightDiscards()
        discardDisplays.values.flatten().forEach { display ->
            if (display.tile == tile) {
                display.entity?.let { e ->
                    e.isGlowing = true
                    (e as Display).glowColorOverride = Color.YELLOW
                }
                highlightedDiscards += display
            }
        }
    }

    private fun unhighlightDiscards() {
        highlightedDiscards.forEach { display ->
            display.entity?.let { e ->
                e.isGlowing = false
                (e as Display).glowColorOverride = null
            }
        }
        highlightedDiscards.clear()
    }

    private fun raiseTileAt(playerUUID: String, index: Int) {
        teleportTileY(handOwnerDisplays[playerUUID]?.getOrNull(index), RAISE_OFFSET)
        teleportTileY(handDisplays[playerUUID]?.getOrNull(index), RAISE_OFFSET)
    }

    private fun lowerTileAt(playerUUID: String, index: Int) {
        teleportTileY(handOwnerDisplays[playerUUID]?.getOrNull(index), -RAISE_OFFSET)
        teleportTileY(handDisplays[playerUUID]?.getOrNull(index), -RAISE_OFFSET)
    }

    private fun teleportTileY(display: MahjongTileDisplay?, deltaY: Double) {
        display ?: return
        display.entity?.let { e ->
            val loc = e.location.clone()
            loc.y += deltaY
            e.teleport(loc)
        }
        display.interactionEntity?.let { e ->
            val loc = e.location.clone()
            loc.y += deltaY
            e.teleport(loc)
        }
    }

    fun spawnActionOptions(playerUUID: String, options: List<ActionDisplayOption>) {
        clearActionOptions(playerUUID)
        if (options.isEmpty()) return

        val player = game.seat.find { it.uuid == playerUUID } ?: return
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        spawnActionButtons(playerUUID, seatIndex, options)
    }

    private fun spawnActionButtons(playerUUID: String, seatIndex: Int, options: List<ActionDisplayOption>) {
        clearActionOptions(playerUUID)

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val dirOffset = 0.85 + DEPTH + HEIGHT + 0.2
        val actionY = surfaceY + HEIGHT + 0.1

        val totalWidth = options.size * ACTION_BUTTON_SPACING
        val startOffset = (totalWidth - ACTION_BUTTON_SPACING) / 2.0

        val displays = mutableListOf<ActionDisplay>()
        val ownerBukkit = Bukkit.getPlayer(UUID.fromString(playerUUID))

        options.forEachIndexed { index, option ->
            val offset = index * ACTION_BUTTON_SPACING
            val x = tableCenter.x + dir[0] * dirOffset + perp[0] * (startOffset - offset)
            val z = tableCenter.z + dir[1] * dirOffset + perp[1] * (startOffset - offset)
            val loc = Location(world, x, actionY, z)

            val textDisplay = world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
            textDisplay.isPersistent = false
            textDisplay.billboard = Display.Billboard.CENTER
            textDisplay.backgroundColor = Color.fromARGB(60, 0, 0, 0)
            textDisplay.brightness = Display.Brightness(15, 15)
            textDisplay.text(
                Component.text(option.label, option.color).decorate(TextDecoration.BOLD)
            )
            textDisplay.alignment = TextDisplay.TextAlignment.CENTER
            textDisplay.setViewRange(0.3f)
            textDisplay.setVisibleByDefault(false)

            val scaleMatrix = org.joml.Matrix4f().scale(0.6f)
            textDisplay.setTransformationMatrix(scaleMatrix)

            ownerBukkit?.showEntity(MahjongCraftPlugin.instance, textDisplay)

            val interLoc = Location(world, x, actionY - 0.1, z)
            val interaction = world.spawnEntity(interLoc, EntityType.INTERACTION) as org.bukkit.entity.Interaction
            interaction.isPersistent = false
            interaction.interactionWidth = 0.4f
            interaction.interactionHeight = 0.4f
            interaction.isResponsive = false

            displays += ActionDisplay(textDisplay, interaction, option.behavior, option.data, playerUUID, option.subOptions)
        }

        actionDisplays[playerUUID] = displays
    }

    fun expandSubMenu(playerUUID: String, subOptions: List<ActionDisplayOption>) {
        val player = game.seat.find { it.uuid == playerUUID } ?: return
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val withSkip = subOptions + ActionDisplayOption(MahjongGameBehavior.SKIP, "跳过", "", NamedTextColor.GRAY)
        spawnActionButtons(playerUUID, seatIndex, withSkip)
    }

    fun clearActionOptions(playerUUID: String) {
        actionDisplays.remove(playerUUID)?.forEach { ad ->
            ad.textDisplay.remove()
            ad.interaction.remove()
        }
    }

    fun getActionByInteraction(interactionUUID: UUID): ActionDisplay? {
        return actionDisplays.values.flatten().find { it.interaction.uniqueId == interactionUUID }
    }

    fun renderDiscards(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val existing = discardDisplays.getOrPut(player.uuid) { mutableListOf() }
        existing.forEach { it.remove() }
        existing.clear()

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val halfSixTiles = WIDTH * 6 / 2.0
        val paddingFromCenter = halfSixTiles + HEIGHT / 2.0 + HEIGHT / 4.0
        val basicOffset = halfSixTiles - WIDTH / 2.0
        val yaw = seatYaw(seatIndex)

        val startX = tableCenter.x + dir[0] * paddingFromCenter + perp[0] * basicOffset
        val startZ = tableCenter.z + dir[1] * paddingFromCenter + perp[1] * basicOffset

        val riichiTile = player.riichiSengenTile
        val halfHeightWidth = (HEIGHT + WIDTH) / 2.0

        player.discardedTilesForDisplay.forEachIndexed { index, tile ->
            val row = index / 6
            val col = index % 6
            val isRiichi = tile == riichiTile

            val colOffset = col * (WIDTH + PADDING)
            val rowOffset = row * (HEIGHT + PADDING)
            val riichiExtra = if (isRiichi) (halfHeightWidth - WIDTH) else 0.0

            val x = startX - perp[0] * (colOffset + riichiExtra) + dir[0] * rowOffset
            val z = startZ - perp[1] * (colOffset + riichiExtra) + dir[1] * rowOffset
            val loc = Location(world, x, flatTileY, z)

            val tileYaw = if (isRiichi) yaw - 90f else yaw
            val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, tileYaw)
            display.spawn()

            showToAllViewers(display)
            existing += display
        }
    }

    fun renderFuuro(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val existing = fuuroDisplays.getOrPut(player.uuid) { mutableListOf() }
        existing.forEach { it.remove() }
        existing.clear()

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val halfTable = 1.5
        val yaw = seatYaw(seatIndex)
        val tileGap = 0.0

        // Place fuuro outside the hand row, to the player's right
        val fuuroDirOffset = 0.85 + DEPTH + HEIGHT + HEIGHT + DEPTH * 2
        var curX = tableCenter.x + dir[0] * fuuroDirOffset - perp[0] * halfTable
        var curZ = tableCenter.z + dir[1] * fuuroDirOffset - perp[1] * halfTable

        var tileCount = 0
        var lastWasClaimTile = false

        player.fuuroList.forEach { fuuro ->
            val isAnkan = fuuro.mentsu is org.mahjong4j.hands.Kantsu && !fuuro.mentsu.isOpen
            val isKakan = fuuro.mentsu is com.mahjongcraft.model.Kakantsu

            if (isAnkan) {
                val tiles = fuuro.tiles
                tiles.forEachIndexed { index, tile ->
                    val stepSize = if (tileCount == 0) WIDTH / 2.0 + tileGap / 2.0
                        else if (lastWasClaimTile) (HEIGHT + WIDTH) / 2.0 + tileGap
                        else WIDTH.toDouble() + tileGap

                    curX += perp[0] * stepSize
                    curZ += perp[1] * stepSize

                    val loc = Location(world, curX, flatTileY, curZ)
                    val face = if (index == 1 || index == 2) TileFace.FACE_UP else TileFace.FACE_DOWN
                    val display = MahjongTileDisplay(loc, tile, face, yaw)
                    display.spawn()
                    showToAllViewers(display)
                    existing += display

                    lastWasClaimTile = false
                    tileCount++
                }
            } else {
                val sortedTiles = if (isKakan) fuuro.tiles.toMutableList()
                    else fuuro.tiles.sortedByDescending { it.sortOrder }.toMutableList()

                val kakanTile = if (isKakan) sortedTiles.removeLast() else null

                sortedTiles.remove(fuuro.claimTile)
                val claimIndex = when (fuuro.claimTarget) {
                    ClaimTarget.LEFT -> { sortedTiles.add(0, fuuro.claimTile); 0 }
                    ClaimTarget.ACROSS -> { sortedTiles.add(1, fuuro.claimTile); 1 }
                    ClaimTarget.RIGHT -> { sortedTiles.add(fuuro.claimTile); sortedTiles.size - 1 }
                    else -> -1
                }

                var claimTileX = 0.0
                var claimTileZ = 0.0

                sortedTiles.forEachIndexed { idx, tile ->
                    val isClaimTile = idx == claimIndex

                    val stepSize = when {
                        tileCount == 0 -> {
                            if (isClaimTile) HEIGHT / 2.0 + tileGap / 2.0
                            else WIDTH / 2.0 + tileGap / 2.0
                        }
                        isClaimTile || lastWasClaimTile -> (HEIGHT + WIDTH) / 2.0 + tileGap
                        else -> WIDTH.toDouble() + tileGap
                    }

                    curX += perp[0] * stepSize
                    curZ += perp[1] * stepSize

                    val halfGap = (HEIGHT - WIDTH) / 2.0
                    val posX = if (isClaimTile) curX + dir[0] * halfGap else curX
                    val posZ = if (isClaimTile) curZ + dir[1] * halfGap else curZ

                    val tileYaw = if (isClaimTile) yaw + 90f else yaw

                    val loc = Location(world, posX, flatTileY, posZ)
                    val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, tileYaw)
                    display.spawn()
                    showToAllViewers(display)
                    existing += display

                    if (isClaimTile) {
                        claimTileX = posX
                        claimTileZ = posZ
                    }
                    lastWasClaimTile = isClaimTile
                    tileCount++
                }

                kakanTile?.let { tile ->
                    val kakanX = claimTileX - dir[0] * (WIDTH.toDouble() + tileGap)
                    val kakanZ = claimTileZ - dir[1] * (WIDTH.toDouble() + tileGap)
                    val loc = Location(world, kakanX, flatTileY, kakanZ)
                    val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw + 90f)
                    display.spawn()
                    showToAllViewers(display)
                    existing += display
                }
            }

            // Gap between fuuro groups
            curX += perp[0] * PADDING
            curZ += perp[1] * PADDING
        }
    }

    fun renderNukiDora(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        val existing = nukiDoraDisplays.getOrPut(player.uuid) { mutableListOf() }
        existing.forEach { it.remove() }
        existing.clear()

        if (player.nukiDoraTiles.isEmpty()) return

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val halfTable = 1.5
        val yaw = seatYaw(seatIndex)
        val handDirOffset = 0.85 + DEPTH + HEIGHT

        player.nukiDoraTiles.forEachIndexed { index, tile ->
            val tileOffset = index * (WIDTH + PADDING)
            val perpPos = halfTable - WIDTH / 2.0 - tileOffset
            val x = tableCenter.x + dir[0] * handDirOffset - perp[0] * perpPos
            val z = tableCenter.z + dir[1] * handDirOffset - perp[1] * perpPos
            val loc = Location(world, x, flatTileY, z)

            val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw)
            display.spawn()
            showToAllViewers(display)
            existing += display
        }
    }

    private fun spawnBotNameTags() {
        clearBotNameTags()
        game.seat.forEachIndexed { seatIndex, player ->
            if (player !is MahjongBot) return@forEachIndexed

            val dir = seatDirection(seatIndex)
            val nameTagDistance = 3.2
            val x = tableCenter.x + dir[0] * nameTagDistance
            val z = tableCenter.z + dir[1] * nameTagDistance
            val loc = Location(world, x, surfaceY + 0.5, z)

            val textDisplay = world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
            textDisplay.isPersistent = false
            textDisplay.text(
                Component.text("\uD83E\uDD16 ", NamedTextColor.GRAY)
                    .append(Component.text(player.displayName, NamedTextColor.GOLD))
            )
            textDisplay.billboard = Display.Billboard.CENTER
            textDisplay.backgroundColor = Color.fromARGB(160, 0, 0, 0)
            textDisplay.brightness = Display.Brightness(15, 15)
            textDisplay.isSeeThrough = false
            textDisplay.setViewRange(0.8f)

            botNameDisplays += textDisplay
        }
    }

    private fun clearBotNameTags() {
        botNameDisplays.forEach { it.remove() }
        botNameDisplays.clear()
    }

    private fun showToAllViewers(display: MahjongTileDisplay) {
        game.players.forEach { p ->
            val bp = Bukkit.getPlayer(UUID.fromString(p.uuid))
            if (bp != null) display.showTo(bp)
        }
        Bukkit.getOnlinePlayers()
            .filter { op -> game.players.none { it.uuid == op.uniqueId.toString() } }
            .forEach { display.showTo(it) }
    }

    private fun updateVisibility(player: MahjongPlayerBase) {
        val backDisplays = handDisplays[player.uuid] ?: return
        val ownerOnlyDisplays = handOwnerDisplays[player.uuid] ?: return
        val ownerBukkit = Bukkit.getPlayer(UUID.fromString(player.uuid))

        val otherBukkitPlayers = game.players
            .filter { it.uuid != player.uuid }
            .mapNotNull { Bukkit.getPlayer(UUID.fromString(it.uuid)) }

        val spectators = if (game.rule.spectate) {
            Bukkit.getOnlinePlayers().filter { op -> game.players.none { it.uuid == op.uniqueId.toString() } }
        } else emptyList()

        backDisplays.forEach { display ->
            if (ownerBukkit != null) display.hideTo(ownerBukkit)
            otherBukkitPlayers.forEach { display.showTo(it) }
            spectators.forEach { display.showTo(it) }
        }

        ownerOnlyDisplays.forEach { display ->
            if (ownerBukkit != null) display.showTo(ownerBukkit)
            otherBukkitPlayers.forEach { display.hideTo(it) }
            spectators.forEach { if (game.rule.spectate) display.showTo(it) else display.hideTo(it) }
        }
    }

    fun revealHands(player: MahjongPlayerBase) {
        val seatIndex = game.seat.indexOf(player)
        if (seatIndex < 0) return

        handDisplays[player.uuid]?.forEach { it.remove() }
        handDisplays[player.uuid]?.clear()
        handOwnerDisplays[player.uuid]?.forEach { it.remove() }
        handOwnerDisplays[player.uuid]?.clear()

        val dir = seatDirection(seatIndex)
        val perp = seatPerpendicular(seatIndex)
        val dirOffset = 0.85 + DEPTH + HEIGHT
        val yaw = seatYaw(seatIndex)
        val tileCount = player.hands.size
        val totalWidth = tileCount * WIDTH + (tileCount - 1) * PADDING
        val startOffset = totalWidth / 2.0

        val revealed = mutableListOf<MahjongTileDisplay>()

        player.hands.forEachIndexed { index, tile ->
            val tileOffset = index * (WIDTH + PADDING)
            val x = tableCenter.x + dir[0] * dirOffset + perp[0] * (startOffset - tileOffset)
            val z = tableCenter.z + dir[1] * dirOffset + perp[1] * (startOffset - tileOffset)
            val loc = Location(world, x, flatTileY, z)

            val display = MahjongTileDisplay(loc, tile, TileFace.FACE_UP, yaw)
            display.spawn()
            showToAllViewers(display)
            revealed += display
        }

        handDisplays[player.uuid] = revealed.toMutableList()
    }

    private fun spawnFloatingCenterTile(tile: MahjongTile) {
        clearFloatingCenterTile()

        val floatY = surfaceY + 0.6
        val loc = Location(world, tableCenter.x, floatY, tableCenter.z)
        val display = MahjongTileDisplay(loc, tile, TileFace.STANDING, 0f)
        display.spawn()
        display.entity?.let { entity ->
            val matrix = org.joml.Matrix4f()
            matrix.scale(0.3f)
            entity.setTransformationMatrix(matrix)
            entity.billboard = Display.Billboard.CENTER
        }
        showToAllViewers(display)
        floatingCenterDisplay = display
    }

    private fun clearFloatingCenterTile() {
        floatingCenterDisplay?.remove()
        floatingCenterDisplay = null
    }

    fun clearAllDisplays() {
        handDisplays.values.flatten().forEach { it.remove() }
        handDisplays.clear()
        handOwnerDisplays.values.flatten().forEach { it.remove() }
        handOwnerDisplays.clear()
        discardDisplays.values.flatten().forEach { it.remove() }
        discardDisplays.clear()
        fuuroDisplays.values.flatten().forEach { it.remove() }
        fuuroDisplays.clear()
        nukiDoraDisplays.values.flatten().forEach { it.remove() }
        nukiDoraDisplays.clear()
        actionDisplays.values.flatten().forEach { ad -> ad.textDisplay.remove(); ad.interaction.remove() }
        actionDisplays.clear()
        clearFloatingCenterTile()
        clearBotNameTags()
    }
}
