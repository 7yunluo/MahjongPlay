package com.mahjongcraft.model

import com.mahjongcraft.util.TextFormatting
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 麻將的規則
 *
 * @param length 遊戲局數, 預設為半莊
 * @param thinkingTime 思考時間, 預設為 5 + 20
 * @param startingPoints 起始點數, 預設 25000
 * @param minPointsToWin 1位必要點數, 預設 30000
 * @param minimumHan 翻縛, 預設 ONE
 * @param spectate 旁觀 (遊戲外玩家能否看到手牌)
 * @param redFive 赤寶牌數量
 * @param openTanyao 食斷
 * @param localYaku 古役
 */
@Serializable
data class MahjongRule(
    var length: GameLength = GameLength.TWO_WIND,
    var playerCount: Int = 4,
    var thinkingTime: ThinkingTime = ThinkingTime.NORMAL,
    var startingPoints: Int = 25000,
    var minPointsToWin: Int = 30000,
    var minimumHan: MinimumHan = MinimumHan.ONE,
    var spectate: Boolean = true,
    var redFive: RedFive = RedFive.THREE,
    var openTanyao: Boolean = true,
    var localYaku: Boolean = false
) {
    val isSanma: Boolean get() = playerCount == 3
    fun toJsonString(): String = Json.encodeToString(serializer(), this)

    fun toComponents(): List<Component> {
        val colon = Component.text(": ").color(NamedTextColor.WHITE)
        val enable = Component.text("开启").color(NamedTextColor.GREEN)
        val disable = Component.text("关闭").color(NamedTextColor.GREEN)
        return listOf(
            Component.text("§c§l规则"),
            Component.text(" - 局数: ").color(NamedTextColor.YELLOW)
                .append(length.toText().color(NamedTextColor.GREEN)),
            Component.text(" - 思考时间: ").color(NamedTextColor.YELLOW)
                .append(Component.text("${thinkingTime.base}").color(NamedTextColor.AQUA))
                .append(Component.text(" + ").color(NamedTextColor.RED))
                .append(Component.text("${thinkingTime.extra}").color(NamedTextColor.AQUA))
                .append(Component.text(" 秒").color(NamedTextColor.GREEN)),
            Component.text(" - 起始点数: ").color(NamedTextColor.YELLOW)
                .append(Component.text("$startingPoints").color(NamedTextColor.GREEN)),
            Component.text(" - 1位必要点数: ").color(NamedTextColor.YELLOW)
                .append(Component.text("$minPointsToWin").color(NamedTextColor.GREEN)),
            Component.text(" - 翻缚: ").color(NamedTextColor.YELLOW)
                .append(Component.text("${minimumHan.han}").color(NamedTextColor.GREEN)),
            Component.text(" - 旁观: ").color(NamedTextColor.YELLOW)
                .append(if (spectate) enable else disable),
            Component.text(" - 赤宝牌: ").color(NamedTextColor.YELLOW)
                .append(Component.text("${redFive.quantity}").color(NamedTextColor.GREEN)),
            Component.text(" - 食断: ").color(NamedTextColor.YELLOW)
                .append(if (openTanyao) enable else disable)
        )
    }

    companion object {
        fun fromJsonString(jsonString: String): MahjongRule = Json.decodeFromString(serializer(), jsonString)
        const val MAX_POINTS = 200000
        const val MIN_POINTS = 100
    }

    /**
     * 麻將遊戲長度
     */
    enum class GameLength(
        private val startingWind: Wind,
        val rounds: Int,
        val finalRound: Pair<Wind, Int>,
        val displayText: String
    ) : TextFormatting {
        ONE_GAME(Wind.EAST, 1, Wind.EAST to 3, "一局"),
        EAST(Wind.EAST, 4, Wind.SOUTH to 3, "东风"),
        TWO_WIND(Wind.EAST, 8, Wind.WEST to 3, "半庄");

        fun getStartingRound(): MahjongRound = MahjongRound(wind = startingWind)

        fun getRounds(playerCount: Int): Int = when (this) {
            ONE_GAME -> 1
            else -> (rounds / 4) * playerCount
        }

        fun getFinalRound(playerCount: Int): Pair<Wind, Int> = when (this) {
            ONE_GAME -> finalRound
            else -> finalRound.first to (playerCount - 1)
        }

        override fun toText(): Component = Component.text(displayText)
    }

    /**
     * 翻縛 (最小翻數限制)
     */
    enum class MinimumHan(val han: Int) : TextFormatting {
        ONE(1),
        TWO(2),
        FOUR(4),
        YAKUMAN(13);

        override fun toText(): Component = Component.text(han.toString())
    }

    /**
     * 思考時間
     */
    enum class ThinkingTime(
        val base: Int,
        val extra: Int
    ) : TextFormatting {
        VERY_SHORT(3, 5),
        SHORT(5, 10),
        NORMAL(5, 20),
        LONG(60, 0),
        VERY_LONG(300, 0);

        override fun toText(): Component = Component.text("$base + $extra s")
    }

    /**
     * 赤寶牌
     */
    enum class RedFive(val quantity: Int) : TextFormatting {
        NONE(0),
        THREE(3),
        FOUR(4);

        override fun toText(): Component = Component.text(quantity.toString())
    }
}
