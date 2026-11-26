package com.zero.accelerator.util

import android.content.Context
import android.os.PowerManager
import com.zero.accelerator.model.PerfMode

/**
 * Screen / CPU wake control.
 *  - LOW / BALANCED : keep the screen on while the game is foreground.
 *  - MONSTER        : also hold a CPU wake lock with a 10-minute timeout.
 * All locks are released the moment the game leaves the foreground, so idle
 * footprint stays at zero.
 */
object WakeLockManager {

    private var cpu: PowerManager.WakeLock? = null
    private var screen: PowerManager.WakeLock? = null

    fun acquire(ctx: Context, mode: PerfMode) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        release()
        screen = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "zero::screen"
        ).apply {
            setReferenceCounted(false)
            if (mode == PerfMode.MONSTER) acquire(10 * 60 * 1000L) else acquire()
        }
        if (mode == PerfMode.MONSTER) {
            cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zero::cpu").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        }
    }

    fun release() {
        cpu?.let { if (it.isHeld) it.release() }
        cpu = null
        screen?.let { if (it.isHeld) it.release() }
        screen = null
    }
}
