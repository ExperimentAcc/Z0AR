package com.zero.accelerator.util

import android.app.NotificationManager
import android.content.Context

object DndUtil {

    fun granted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted

    /** Flip between "all" and "priority only" (which keeps our high-priority notif). */
    fun toggle(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (!mgr.isNotificationPolicyAccessGranted) return
        val cur = mgr.currentInterruptionFilter
        mgr.setInterruptionFilter(
            if (cur == NotificationManager.INTERRUPTION_FILTER_ALL)
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    fun openSettings(ctx: Context) = Perms.openDnd(ctx)
}
