package com.mahjongcraft.util

import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman

object YakuNameChinese {

    private val normalYakuNames = mapOf(
        NormalYaku.TANYAO to "断幺九",
        NormalYaku.TSUMO to "门前清自摸和",
        NormalYaku.PINFU to "平和",
        NormalYaku.IPEIKO to "一杯口",
        NormalYaku.HAKU to "白",
        NormalYaku.HATSU to "发",
        NormalYaku.CHUN to "中",
        NormalYaku.JIKAZE to "自风牌",
        NormalYaku.BAKAZE to "场风牌",
        NormalYaku.IPPATSU to "一发",
        NormalYaku.HOUTEI to "河底捞鱼",
        NormalYaku.HAITEI to "海底摸月",
        NormalYaku.REACH to "立直",
        NormalYaku.DORA to "宝牌",
        NormalYaku.URADORA to "里宝牌",
        NormalYaku.RINSHANKAIHOH to "岭上开花",
        NormalYaku.CHANKAN to "抢杠",
        NormalYaku.DOUBLE_REACH to "双立直",
        NormalYaku.CHANTA to "混全带幺九",
        NormalYaku.HONROHTOH to "混老头",
        NormalYaku.SANSHOKUDOHJUN to "三色同顺",
        NormalYaku.IKKITSUKAN to "一气通贯",
        NormalYaku.TOITOIHO to "对对和",
        NormalYaku.SANSHOKUDOHKO to "三色同刻",
        NormalYaku.SANANKO to "三暗刻",
        NormalYaku.SANKANTSU to "三杠子",
        NormalYaku.SHOSANGEN to "小三元",
        NormalYaku.CHITOITSU to "七对子",
        NormalYaku.RYANPEIKO to "二杯口",
        NormalYaku.JUNCHAN to "纯全带幺九",
        NormalYaku.HONITSU to "混一色",
        NormalYaku.CHINITSU to "清一色",
    )

    private val yakumanNames = mapOf(
        Yakuman.KOKUSHIMUSO to "国士无双",
        Yakuman.SUANKO to "四暗刻",
        Yakuman.CHURENPOHTO to "九莲宝灯",
        Yakuman.DAISANGEN to "大三元",
        Yakuman.TSUISO to "字一色",
        Yakuman.SHOSUSHI to "小四喜",
        Yakuman.DAISUSHI to "大四喜",
        Yakuman.RYUISO to "绿一色",
        Yakuman.CHINROTO to "清老头",
        Yakuman.SUKANTSU to "四杠子",
        Yakuman.RENHO to "人和",
        Yakuman.CHIHO to "地和",
        Yakuman.TENHO to "天和",
    )

    fun getName(yaku: NormalYaku): String = normalYakuNames[yaku] ?: yaku.japanese
    fun getName(yakuman: Yakuman): String = yakumanNames[yakuman] ?: yakuman.japanese
}
