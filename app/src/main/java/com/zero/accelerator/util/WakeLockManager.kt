package com.zero.accelerator.util

import android.content.Context
import android.os.PowerManager
import com.zero.accelerator.model.PerfMode

/**
 * MONSTER holds a partial (CPU) wake lock. The screen is kept awake by the overlay
 * window flag instead, so nothing leaks if the service dies.
 */
object WakeLockManager {

    private var cpu: PowerManager.WakeLock? = null

    fun acquire(ctx: Context, mode: PerfMode) {
        release()
        if (mode != PerfMode.MONSTER) return
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zero:cpu").apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            cpu?.let { if (it.isHeld) it.release() }
        } catch (_: Throwable) {
        }
        cpu = null
    }
}
