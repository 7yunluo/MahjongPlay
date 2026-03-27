package com.mahjongplay.model

/**
 * 鳴牌的對象
 */
enum class ClaimTarget {
    SELF,   // 自己
    RIGHT,  // 右邊 (下家)
    LEFT,   // 左邊 (上家)
    ACROSS  // 對面
}
