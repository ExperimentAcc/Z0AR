package com.zero.accelerator.util

import android.app.NotificationManager
import android.content.Context

object DndUtil {

    private fun mgr(ctx: Context): NotificationManager =
        ctx.getSystemService(NotificationManager::class.java)

    fun granted(ctx: Context): Boolean = mgr(ctx).isNotificationPolicyAccessGranted

    fun filter(ctx: Context): Int = mgr(ctx).currentInterruptionFilter

    fun active(ctx: Context): Boolean {
        if (!granted(ctx)) return false
        val f = filter(ctx)
        return f == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            f == NotificationManager.INTERRUPTION_FILTER_NONE ||
            f == NotificationManager.INTERRUPTION_FILTER_ALARMS
    }

    fun set(ctx: Context, filter: Int) {
        if (!granted(ctx)) return
        try {
            mgr(ctx).setInterruptionFilter(filter)
        } catch (_: Throwable) {
        }
    }
}
