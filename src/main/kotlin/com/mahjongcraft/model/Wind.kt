package com.mahjongcraft.model

import com.mahjongcraft.util.TextFormatting
import net.kyori.adventure.text.Component
import org.mahjong4j.tile.Tile

/**
 * 風位
 */
enum class Wind(
    val tile: Tile,
    val displayName: String
) : TextFormatting {
    EAST(Tile.TON, "東"),
    SOUTH(Tile.NAN, "南"),
    WEST(Tile.SHA, "西"),
    NORTH(Tile.PEI, "北");

    override fun toText(): Component = Component.text(displayName)
}
