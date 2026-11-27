package com.zero.accelerator.model

import androidx.annotation.ColorInt

/** Keys stay identical to v1 so saved preferences keep working. */
enum class PerfMode(
    val key: String,
    val label: String,
    val short: String,
    val desc: String,
    @ColorInt val color: Int
) {
    LOW("low", "Battery Saver", "SAVER",
        "Light cleanup. Stops heavy background apps only.", 0xFF3DDC84.toInt()),
    BALANCED("balanced", "Balanced", "BALANCED",
        "Full RAM purge, cache trim, game set to active standby bucket.", 0xFF4DA3FF.toInt()),
    MONSTER("monster", "MONSTER", "MONSTER",
        "Everything above plus fixed performance mode and CPU wake lock.", 0xFFFFC72C.toInt());

    val level: Int get() = ordinal

    companion object {
        fun fromKey(k: String?): PerfMode = values().firstOrNull { it.key == k } ?: BALANCED
    }
}
