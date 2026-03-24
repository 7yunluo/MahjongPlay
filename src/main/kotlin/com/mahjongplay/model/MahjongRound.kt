package com.mahjongplay.model

import kotlinx.serialization.Serializable

/**
 * 麻將的回合
 *
 * @param wind 場風 (Prevalent Wind)
 * @param round 第幾局 (從 0 開始, 3 為結束)
 * @param honba 本場數
 */
@Serializable
data class MahjongRound(
    var wind: Wind = Wind.EAST,
    var round: Int = 0,
    var honba: Int = 0
) {
    private var spentRounds = 0

    /**
     * 換下個回合
     */
    fun nextRound(playerCount: Int = 4) {
        val nextRound = (this.round + 1) % playerCount
        honba = 0
        if (nextRound == 0) {
            val nextWindNum = (this.wind.ordinal + 1) % 4
            wind = Wind.entries[nextWindNum]
        }
        round = nextRound
        spentRounds++
    }

    fun isAllLast(rule: MahjongRule): Boolean = (spentRounds + 1) >= rule.length.getRounds(rule.playerCount)

    /**
     * 顯示用的回合名 (ex: "東1局")
     */
    fun displayName(): String = "${wind.displayName}${round + 1}局"
}
