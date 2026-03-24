package com.mahjongcraft.util

import net.kyori.adventure.text.Component

/**
 * Formatting interface for game elements that can be represented as text.
 * Replaces the original Fabric mod's TextFormatting interface.
 */
interface TextFormatting {
    fun toText(): Component
}
