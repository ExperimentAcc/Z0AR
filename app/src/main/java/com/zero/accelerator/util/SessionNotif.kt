package com.zero.accelerator.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zero.accelerator.C
import com.zero.accelerator.R
import com.zero.accelerator.model.PerfMode

/**
 * High-priority, ongoing session notification used by MONSTER mode so the
 * accelerator cannot be silently killed while the game runs.
 */
object SessionNotif {

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(C.NOTIF_CHANNEL) == null) {
                val ch = NotificationChannel(
                    C.NOTIF_CHANNEL,
                    "Game Session",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { setShowBadge(false) }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun show(ctx: Context, mode: PerfMode, target: String?) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, C.NOTIF_CHANNEL)
            .setContentTitle("ZERO ACCELERATOR")
            .setContentText("${mode.label} mode active · ${target ?: ""}")
            .setSmallIcon(R.drawable.ic_notify)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(C.NOTIF_ID, n)
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(C.NOTIF_ID)
    }
}
