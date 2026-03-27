package com.mahjongplay.model

import org.mahjong4j.hands.Mentsu

/**
 * 副露 (鳴牌/call)
 *
 * Abstracted from original: uses MahjongTile instead of MahjongTileEntity.
 * The tile entity references are managed separately by the display layer.
 *
 * @param mentsu mahjong4j 的面子
 * @param tiles 副露中的牌
 * @param claimTarget 鳴牌對象
 * @param claimTile 被鳴的那張牌
 */
class Fuuro(
    val mentsu: Mentsu,
    val tiles: List<MahjongTile>,
    val claimTarget: ClaimTarget,
    val claimTile: MahjongTile
)
