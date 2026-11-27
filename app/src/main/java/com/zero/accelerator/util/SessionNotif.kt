package com.zero.accelerator.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.zero.accelerator.C
import com.zero.accelerator.R
import com.zero.accelerator.model.PerfMode

/** Ongoing notification shown while MONSTER mode is active. */
object SessionNotif {

    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(C.NOTIF_CHANNEL) == null) {
            val ch = NotificationChannel(
                C.NOTIF_CHANNEL, "Game Session", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            mgr.createNotificationChannel(ch)
        }
    }

    fun show(ctx: Context, mode: PerfMode, target: String?) {
        try {
            ensureChannel(ctx)
            val n = NotificationCompat.Builder(ctx, C.NOTIF_CHANNEL)
                .setContentTitle("ZERO ACCELERATOR")
                .setContentText("${mode.label} mode active  ${target ?: ""}")
                .setSmallIcon(R.drawable.ic_notify)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(C.NOTIF_ID, n)
        } catch (_: Throwable) {
        }
    }

    fun cancel(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(C.NOTIF_ID)
        } catch (_: Throwable) {
        }
    }
}
