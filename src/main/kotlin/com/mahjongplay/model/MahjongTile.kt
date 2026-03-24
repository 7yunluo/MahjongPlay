package com.mahjongplay.model

import com.mahjongplay.util.TextFormatting
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType

/**
 * 所有麻將牌
 */
enum class MahjongTile : TextFormatting {
    M1, M2, M3, M4, M5, M6, M7, M8, M9,
    P1, P2, P3, P4, P5, P6, P7, P8, P9,
    S1, S2, S3, S4, S5, S6, S7, S8, S9,

    EAST,   // 東
    SOUTH,  // 南
    WEST,   // 西
    NORTH,  // 北

    WHITE_DRAGON, // 白
    GREEN_DRAGON, // 発
    RED_DRAGON,   // 中

    M5_RED,
    P5_RED,
    S5_RED,

    UNKNOWN;

    /**
     * 這張牌的資源包模型路徑
     */
    val modelPath: String
        get() = "mahjongcraft:mahjong_tile_${name.lowercase()}"

    /**
     * 是否是赤牌
     */
    val isRed: Boolean
        get() = this == M5_RED || this == P5_RED || this == S5_RED

    /**
     * 對應 code 編號 (用來決定牌的外觀), 直接使用 ordinal
     */
    val code: Int = ordinal

    /**
     * 對應 mahjong4j 之中的 Tile
     */
    val mahjong4jTile: Tile
        get() {
            val tileCode = when (code) {
                M5_RED.code -> M5.code
                P5_RED.code -> P5.code
                S5_RED.code -> S5.code
                UNKNOWN.code -> M1.code
                else -> code
            }
            return Tile.valueOf(tileCode)
        }

    /**
     * 這張牌的排列順序, 只有紅寶牌有調整
     */
    val sortOrder: Int
        get() = when (this) {
            M5_RED -> 4
            P5_RED -> 13
            S5_RED -> 22
            else -> code
        }

    /**
     * 取得在順序上的下一張牌
     */
    val nextTile: MahjongTile
        get() {
            with(mahjong4jTile) {
                val nextTileCode = when (type) {
                    TileType.FONPAI -> if (this == Tile.PEI) Tile.TON.code else code + 1
                    TileType.SANGEN -> if (this == Tile.CHN) Tile.HAK.code else code + 1
                    else -> if (number == 9) code - 8 else code + 1
                }
                return entries[nextTileCode]
            }
        }

    /**
     * 取得在順序上的上一張牌
     */
    val previousTile: MahjongTile
        get() {
            with(mahjong4jTile) {
                val previousTileCode = when (type) {
                    TileType.FONPAI -> if (this == Tile.TON) Tile.PEI.code else code - 1
                    TileType.SANGEN -> if (this == Tile.HAK) Tile.CHN.code else code - 1
                    else -> if (number == 1) code + 8 else code - 1
                }
                return entries[previousTileCode]
            }
        }

    override fun toText(): Component {
        return when (mahjong4jTile.type) {
            TileType.MANZU -> Component.text("${mahjong4jTile.number}万")
            TileType.PINZU -> Component.text("${mahjong4jTile.number}筒")
            TileType.SOHZU -> Component.text("${mahjong4jTile.number}索")
            TileType.FONPAI, TileType.SANGEN -> Component.text(
                when (this) {
                    EAST, M5_RED -> "東"   // M5_RED won't hit here, but exhaustive
                    SOUTH, P5_RED -> "南"
                    WEST, S5_RED -> "西"
                    NORTH -> "北"
                    WHITE_DRAGON -> "白"
                    GREEN_DRAGON -> "発"
                    RED_DRAGON -> "中"
                    else -> name
                }
            )
            else -> Component.text(name)
        }.let {
            if (isRed) it.color(NamedTextColor.RED) else it
        }
    }

    /**
     * 簡短的中文顯示名
     */
    val displayName: String
        get() = when (this) {
            M1 -> "一万"; M2 -> "二万"; M3 -> "三万"; M4 -> "四万"; M5 -> "五万"
            M6 -> "六万"; M7 -> "七万"; M8 -> "八万"; M9 -> "九万"
            P1 -> "一筒"; P2 -> "二筒"; P3 -> "三筒"; P4 -> "四筒"; P5 -> "五筒"
            P6 -> "六筒"; P7 -> "七筒"; P8 -> "八筒"; P9 -> "九筒"
            S1 -> "一索"; S2 -> "二索"; S3 -> "三索"; S4 -> "四索"; S5 -> "五索"
            S6 -> "六索"; S7 -> "七索"; S8 -> "八索"; S9 -> "九索"
            EAST -> "東"; SOUTH -> "南"; WEST -> "西"; NORTH -> "北"
            WHITE_DRAGON -> "白"; GREEN_DRAGON -> "発"; RED_DRAGON -> "中"
            M5_RED -> "赤五万"; P5_RED -> "赤五筒"; S5_RED -> "赤五索"
            UNKNOWN -> "?"
        }

    fun doraFromIndicator(isSanma: Boolean = false): MahjongTile {
        if (isSanma && this == M1) return M9
        return nextTile
    }

    companion object {

        fun random(): MahjongTile = entries.toTypedArray().random()

        val normalWall: List<MahjongTile> = buildList {
            entries.forEach { tile ->
                repeat(4) { this += tile }
                if (tile == RED_DRAGON) return@buildList
            }
        }

        private val sanmaTilesRemoved = setOf(M2, M3, M4, M5, M6, M7, M8)

        val sanmaWall: List<MahjongTile> = normalWall.filter { it !in sanmaTilesRemoved }

        val redFive3Wall: List<MahjongTile> = normalWall.toMutableList().apply {
            this -= M5
            this -= P5
            this -= S5
            this += M5_RED
            this += P5_RED
            this += S5_RED
        }

        val redFive4Wall: List<MahjongTile> = redFive3Wall.toMutableList().apply {
            this -= P5
            this += P5_RED
        }

        val sanmaRedFive3Wall: List<MahjongTile> = sanmaWall.toMutableList().apply {
            this -= P5
            this -= S5
            this += P5_RED
            this += S5_RED
        }

        val sanmaRedFive4Wall: List<MahjongTile> = sanmaRedFive3Wall.toMutableList().apply {
            this -= P5
            this += P5_RED
        }
    }
}
