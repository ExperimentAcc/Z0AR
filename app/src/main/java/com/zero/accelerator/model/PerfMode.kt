package com.zero.accelerator.model

import androidx.annotation.ColorInt

enum class PerfMode(
    val key: String,
    val label: String,
    @ColorInt val color: Int,
    val hex: String
) {
    LOW("low", "LOW", 0xFF00E676.toInt(), "#00E676"),
    BALANCED("balanced", "BALANCED", 0xFF2979FF.toInt(), "#2979FF"),
    MONSTER("monster", "MONSTER", 0xFFFF1744.toInt(), "#FF1744");

    companion object {
        fun fromKey(k: String?): PerfMode =
            entries.firstOrNull { it.key == k } ?: BALANCED
    }
}
