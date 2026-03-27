package com.mahjongplay.game

import com.mahjongplay.model.*
import java.util.UUID

class MahjongBot(
    override val displayName: String = "Bot",
    val botTileCode: Int = MahjongTile.random().code
) : MahjongPlayerBase() {
    override val uuid: String = UUID.randomUUID().toString()
    override val isRealPlayer = false
    override var ready: Boolean = true

    override suspend fun askToKita(): Boolean = true
}
