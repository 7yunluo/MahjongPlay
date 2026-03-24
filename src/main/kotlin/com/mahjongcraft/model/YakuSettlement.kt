package com.mahjongcraft.model

import kotlinx.serialization.Serializable
import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman

/**
 * 役結算, 用於顯示和牌結果
 *
 * @param displayName 胡牌玩家的 displayName
 * @param uuid 胡牌玩家的 stringUUID
 * @param nagashiMangan 流局滿貫專用
 * @param redFiveCount 紅寶牌數量
 * @param riichi 立直
 * @param winningTile 胡牌的那張牌
 * @param hands 手牌
 * @param fuuroList 副露列表 (是否為暗槓, 牌列表)
 * @param doraIndicators 寶牌指示牌
 * @param uraDoraIndicators 裏寶牌指示牌
 * @param score 顯示用分數
 * @param botCode 機器人外觀碼
 */
@Serializable
data class YakuSettlement(
    val displayName: String,
    val uuid: String,
    val isRealPlayer: Boolean,
    val botCode: Int = MahjongTile.UNKNOWN.code,
    val yakuList: List<NormalYaku>,
    val yakumanList: List<Yakuman>,
    val doubleYakumanList: List<DoubleYakuman>,
    val nagashiMangan: Boolean = false,
    val redFiveCount: Int = 0,
    val nukiDoraCount: Int = 0,
    val riichi: Boolean,
    val winningTile: MahjongTile,
    val hands: List<MahjongTile>,
    val fuuroList: List<Pair<Boolean, List<MahjongTile>>>,
    val doraIndicators: List<MahjongTile>,
    val uraDoraIndicators: List<MahjongTile>,
    val fu: Int,
    val han: Int,
    val score: Int,
) {
    companion object {
        /**
         * 流局滿貫
         */
        fun nagashiMangan(
            playerDisplayName: String,
            playerUUID: String,
            isRealPlayer: Boolean,
            botCode: Int,
            riichi: Boolean,
            hands: List<MahjongTile>,
            doraIndicators: List<MahjongTile>,
            uraDoraIndicators: List<MahjongTile>,
            isDealer: Boolean
        ): YakuSettlement = YakuSettlement(
            displayName = playerDisplayName,
            uuid = playerUUID,
            isRealPlayer = isRealPlayer,
            botCode = botCode,
            yakuList = listOf(),
            yakumanList = listOf(),
            doubleYakumanList = listOf(),
            nagashiMangan = true,
            winningTile = MahjongTile.UNKNOWN,
            riichi = riichi,
            hands = hands,
            fuuroList = listOf(),
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = 0,
            han = 0,
            score = if (isDealer) 12000 else 8000
        )

        /**
         * 無役, 判斷是否聽牌用
         */
        val NO_YAKU = YakuSettlement(
            displayName = "",
            uuid = "",
            isRealPlayer = false,
            botCode = MahjongTile.UNKNOWN.code,
            yakuList = emptyList(),
            yakumanList = emptyList(),
            doubleYakumanList = emptyList(),
            nagashiMangan = false,
            redFiveCount = 0,
            riichi = false,
            winningTile = MahjongTile.UNKNOWN,
            hands = emptyList(),
            fuuroList = emptyList(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList(),
            fu = 0,
            han = 0,
            score = 0
        )
    }
}
