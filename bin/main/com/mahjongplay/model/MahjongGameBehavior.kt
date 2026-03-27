package com.mahjongplay.model

import com.mahjongplay.util.TextFormatting
import net.kyori.adventure.text.Component

/**
 * 麻將遊戲行為
 * 用於伺服端與客戶端之間的通信 (在 Paper 版中用於聊天欄點擊交互)
 */
enum class MahjongGameBehavior(
    private val displayName: String
) : TextFormatting {
    CHII("吃"),
    PON_OR_CHII("碰/吃"),
    PON("碰"),
    KAN("杠"),
    MINKAN("明杠"),
    ANKAN("暗杠"),
    ANKAN_OR_KAKAN("暗杠/加杠"),
    KAKAN("加杠"),
    CHAN_KAN("抢杠"),
    RIICHI("立直"),
    DOUBLE_RIICHI("双立直"),
    KITA("拔北"),
    RON("荣"),
    TSUMO("自摸"),
    KYUUSHU_KYUUHAI("九种九牌"),

    EXHAUSTIVE_DRAW("流局"),
    DISCARD("出牌"),
    SKIP("跳过"),
    GAME_START("游戏开始"),
    GAME_OVER("游戏结束"),
    SCORE_SETTLEMENT("分数结算"),
    YAKU_SETTLEMENT("役结算"),
    COUNTDOWN_TIME("倒数"),
    AUTO_ARRANGE("自动理牌"),
    MACHI("听牌");

    override fun toText(): Component = Component.text(displayName)
}
