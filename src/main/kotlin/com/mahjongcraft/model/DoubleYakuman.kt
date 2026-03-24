package com.mahjongcraft.model

import com.mahjongcraft.util.TextFormatting
import net.kyori.adventure.text.Component

/**
 * 雙倍役滿 (大四喜, 四暗刻單騎, 純正九蓮寶燈, 國士無雙十三面)
 */
enum class DoubleYakuman(
    private val displayName: String
) : TextFormatting {
    DAISUSHI("大四喜"),
    SUANKO_TANKI("四暗刻单骑"),
    JUNSEI_CHURENPOHTO("纯正九莲宝灯"),
    KOKUSHIMUSO_JUSANMENMACHI("国士无双十三面");

    override fun toText(): Component = Component.text(displayName)
}
