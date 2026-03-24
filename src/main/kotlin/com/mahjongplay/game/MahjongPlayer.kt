package com.mahjongplay.game

import com.mahjongplay.interaction.ChatActionSender
import com.mahjongplay.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

data class PendingAction(
    val behaviors: List<MahjongGameBehavior>,
    val deferred: CompletableDeferred<Pair<MahjongGameBehavior, String>>
)

data class ActionDisplayOption(
    val behavior: MahjongGameBehavior,
    val label: String,
    val data: String,
    val color: NamedTextColor = NamedTextColor.WHITE,
    val subOptions: List<ActionDisplayOption>? = null
)

interface PendingActionListener {
    fun onPendingActionStart(player: MahjongPlayer, behaviors: List<MahjongGameBehavior>, timeoutSeconds: Int)
    fun onPendingActionEnd(player: MahjongPlayer)
}

class MahjongPlayer(
    override val uuid: String,
    override val displayName: String,
) : MahjongPlayerBase() {
    override val isRealPlayer = true
    var pendingAction: PendingAction? = null
    var gameId: UUID = UUID.randomUUID()
    var pendingActionListener: PendingActionListener? = null
    var actionOptions: List<ActionDisplayOption> = emptyList()

    private val bukkitPlayer: Player? get() = Bukkit.getPlayer(UUID.fromString(uuid))

    private suspend fun <T> waitForBehaviorResult(
        behavior: MahjongGameBehavior,
        waitingBehaviors: List<MahjongGameBehavior> = listOf(behavior, MahjongGameBehavior.SKIP),
        timeoutSeconds: Int = basicThinkingTime + extraThinkingTime,
        onResult: (MahjongGameBehavior, String) -> T,
    ): T {
        val deferred = CompletableDeferred<Pair<MahjongGameBehavior, String>>()
        pendingAction = PendingAction(waitingBehaviors, deferred)
        val effectiveTimeout = if (timeoutSeconds > 0) timeoutSeconds else 15
        pendingActionListener?.onPendingActionStart(this, waitingBehaviors, effectiveTimeout)
        val result = withTimeoutOrNull(effectiveTimeout * 1000L) { deferred.await() }
            ?: (MahjongGameBehavior.SKIP to "")
        pendingAction = null
        actionOptions = emptyList()
        pendingActionListener?.onPendingActionEnd(this)
        return onResult(result.first, result.second)
    }

    fun resolveAction(behavior: MahjongGameBehavior, data: String = ""): Boolean {
        val pending = pendingAction ?: return false
        if (behavior !in pending.behaviors) return false
        return pending.deferred.complete(behavior to data)
    }

    private val skipOption = ActionDisplayOption(MahjongGameBehavior.SKIP, "跳过", "", NamedTextColor.GRAY)

    override suspend fun askToDiscardTile(
        timeoutTile: MahjongTile,
        cannotDiscardTiles: List<MahjongTile>,
        skippable: Boolean,
    ): MahjongTile {
        actionOptions = emptyList()
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.DISCARD,
            waitingBehaviors = if (skippable) listOf(MahjongGameBehavior.DISCARD, MahjongGameBehavior.SKIP) else listOf(MahjongGameBehavior.DISCARD)
        ) { behavior, data ->
            val tileCode = data.toIntOrNull() ?: return@waitForBehaviorResult timeoutTile
            if (behavior == MahjongGameBehavior.DISCARD) {
                MahjongTile.entries.find { it.code == tileCode } ?: timeoutTile
            } else timeoutTile
        }
    }

    override suspend fun askToChii(
        tile: MahjongTile,
        tilePairs: List<Pair<MahjongTile, MahjongTile>>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? {
        val subs = tilePairs.map { (a, b) ->
            ActionDisplayOption(MahjongGameBehavior.CHII, "${a.displayName}+${b.displayName}", "${a.code},${b.code}", NamedTextColor.GREEN)
        }
        actionOptions = if (subs.size == 1) {
            listOf(subs[0].copy(label = "吃 ${subs[0].label}"), skipOption)
        } else {
            listOf(ActionDisplayOption(MahjongGameBehavior.CHII, "吃", "", NamedTextColor.GREEN, subOptions = subs), skipOption)
        }
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.CHII
        ) { behavior, data ->
            if (behavior == MahjongGameBehavior.CHII) {
                val parts = data.split(",")
                if (parts.size == 2) {
                    val c1 = parts[0].toIntOrNull()
                    val c2 = parts[1].toIntOrNull()
                    val p1 = MahjongTile.entries.find { it.code == c1 }
                    val p2 = MahjongTile.entries.find { it.code == c2 }
                    if (p1 != null && p2 != null) {
                        val pair = p1 to p2
                        if (pair in tilePairs) return@waitForBehaviorResult pair
                    }
                }
            }
            null
        }
    }

    override suspend fun askToPonOrChii(
        tile: MahjongTile,
        tilePairsForChii: List<Pair<MahjongTile, MahjongTile>>,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Pair<MahjongTile, MahjongTile>? {
        val chiiSubs = tilePairsForChii.map { (a, b) ->
            ActionDisplayOption(MahjongGameBehavior.CHII, "${a.displayName}+${b.displayName}", "${a.code},${b.code}", NamedTextColor.GREEN)
        }
        actionOptions = buildList {
            add(ActionDisplayOption(MahjongGameBehavior.PON, "碰", "", NamedTextColor.BLUE))
            if (chiiSubs.size == 1) {
                add(chiiSubs[0].copy(label = "吃 ${chiiSubs[0].label}"))
            } else {
                add(ActionDisplayOption(MahjongGameBehavior.CHII, "吃", "", NamedTextColor.GREEN, subOptions = chiiSubs))
            }
            add(skipOption)
        }
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.PON_OR_CHII,
            waitingBehaviors = listOf(MahjongGameBehavior.CHII, MahjongGameBehavior.PON, MahjongGameBehavior.SKIP)
        ) { behavior, data ->
            when (behavior) {
                MahjongGameBehavior.CHII -> {
                    val parts = data.split(",")
                    if (parts.size == 2) {
                        val c1 = parts[0].toIntOrNull()
                        val c2 = parts[1].toIntOrNull()
                        val p1 = MahjongTile.entries.find { it.code == c1 }
                        val p2 = MahjongTile.entries.find { it.code == c2 }
                        if (p1 != null && p2 != null) {
                            val pair = p1 to p2
                            if (pair in tilePairsForChii) return@waitForBehaviorResult pair
                        }
                    }
                    null
                }
                MahjongGameBehavior.PON -> tile to tile
                else -> null
            }
        }
    }

    override suspend fun askToPon(
        tile: MahjongTile,
        tilePairForPon: Pair<MahjongTile, MahjongTile>,
        target: ClaimTarget,
    ): Boolean {
        actionOptions = listOf(
            ActionDisplayOption(MahjongGameBehavior.PON, "碰", "", NamedTextColor.BLUE),
            skipOption
        )
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.PON
        ) { behavior, _ ->
            behavior == MahjongGameBehavior.PON
        }
    }

    override suspend fun askToAnkanOrKakan(
        canAnkanTiles: Set<MahjongTile>,
        canKakanTiles: Set<Pair<MahjongTile, ClaimTarget>>,
        rule: MahjongRule,
    ): MahjongTile? {
        val kanSubs = buildList {
            canAnkanTiles.forEach { t ->
                add(ActionDisplayOption(MahjongGameBehavior.ANKAN_OR_KAKAN, "暗杠 ${t.displayName}", "${t.code}", NamedTextColor.DARK_AQUA))
            }
            canKakanTiles.forEach { (t, _) ->
                add(ActionDisplayOption(MahjongGameBehavior.ANKAN_OR_KAKAN, "加杠 ${t.displayName}", "${t.code}", NamedTextColor.AQUA))
            }
        }
        actionOptions = if (kanSubs.size == 1) {
            listOf(kanSubs[0], skipOption)
        } else {
            listOf(ActionDisplayOption(MahjongGameBehavior.ANKAN_OR_KAKAN, "杠", "", NamedTextColor.DARK_AQUA, subOptions = kanSubs), skipOption)
        }
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.ANKAN_OR_KAKAN
        ) { behavior, data ->
            if (behavior == MahjongGameBehavior.ANKAN_OR_KAKAN) {
                val code = data.toIntOrNull()
                val tile = MahjongTile.entries.find { it.code == code }
                if (tile != null && (tile in canAnkanTiles || tile in canKakanTiles.map { it.first })) {
                    return@waitForBehaviorResult tile
                }
            }
            null
        }
    }

    override suspend fun askToMinkanOrPon(
        tile: MahjongTile,
        target: ClaimTarget,
        rule: MahjongRule,
    ): MahjongGameBehavior {
        actionOptions = listOf(
            ActionDisplayOption(MahjongGameBehavior.MINKAN, "明杠", "", NamedTextColor.DARK_AQUA),
            ActionDisplayOption(MahjongGameBehavior.PON, "碰", "", NamedTextColor.BLUE),
            skipOption
        )
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.MINKAN,
            waitingBehaviors = listOf(MahjongGameBehavior.PON, MahjongGameBehavior.MINKAN, MahjongGameBehavior.SKIP)
        ) { behavior, _ ->
            when (behavior) {
                MahjongGameBehavior.PON, MahjongGameBehavior.MINKAN -> behavior
                else -> MahjongGameBehavior.SKIP
            }
        }
    }

    override suspend fun askToRiichi(
        tilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>>
    ): MahjongTile? {
        val riichiSubs = tilePairsForRiichi.map { (tile, machi) ->
            val machiStr = machi.joinToString("") { it.displayName }
            ActionDisplayOption(MahjongGameBehavior.RIICHI, "${tile.displayName}→$machiStr", "${tile.code}", NamedTextColor.LIGHT_PURPLE)
        }
        actionOptions = if (riichiSubs.size == 1) {
            listOf(riichiSubs[0].copy(label = "立直 ${riichiSubs[0].label}"), skipOption)
        } else {
            listOf(ActionDisplayOption(MahjongGameBehavior.RIICHI, "立直", "", NamedTextColor.LIGHT_PURPLE, subOptions = riichiSubs), skipOption)
        }
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.RIICHI
        ) { behavior, data ->
            if (behavior == MahjongGameBehavior.RIICHI) {
                val code = data.toIntOrNull()
                val tile = MahjongTile.entries.find { it.code == code }
                if (tile != null && tile in tilePairsForRiichi.map { it.first }) {
                    return@waitForBehaviorResult tile
                }
            }
            null
        }
    }

    override suspend fun askToTsumo(): Boolean {
        actionOptions = listOf(
            ActionDisplayOption(MahjongGameBehavior.TSUMO, "自摸", "", NamedTextColor.GOLD),
            skipOption
        )
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.TSUMO
        ) { behavior, _ ->
            behavior == MahjongGameBehavior.TSUMO
        }
    }

    override suspend fun askToRon(tile: MahjongTile, target: ClaimTarget): Boolean {
        actionOptions = listOf(
            ActionDisplayOption(MahjongGameBehavior.RON, "荣", "", NamedTextColor.RED),
            skipOption
        )
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.RON
        ) { behavior, _ ->
            behavior == MahjongGameBehavior.RON
        }
    }

    override suspend fun askToKyuushuKyuuhai(): Boolean {
        actionOptions = listOf(
            ActionDisplayOption(MahjongGameBehavior.KYUUSHU_KYUUHAI, "九种九牌", "", NamedTextColor.YELLOW),
            skipOption
        )
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.KYUUSHU_KYUUHAI
        ) { behavior, _ ->
            behavior == MahjongGameBehavior.KYUUSHU_KYUUHAI
        }
    }

    override suspend fun askToKita(): Boolean {
        actionOptions = listOf(
            ActionDisplayOption(MahjongGameBehavior.KITA, "拔北", "", NamedTextColor.DARK_GREEN),
            skipOption
        )
        return waitForBehaviorResult(
            behavior = MahjongGameBehavior.KITA
        ) { behavior, _ ->
            behavior == MahjongGameBehavior.KITA
        }
    }
}
