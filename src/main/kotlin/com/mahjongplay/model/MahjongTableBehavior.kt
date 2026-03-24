package com.mahjongplay.model

/**
 * 麻將桌行為 (用於指令/交互處理)
 */
enum class MahjongTableBehavior {
    JOIN,
    LEAVE,
    READY,
    NOT_READY,
    START,
    KICK,
    ADD_BOT,
    CHANGE_RULE
}
