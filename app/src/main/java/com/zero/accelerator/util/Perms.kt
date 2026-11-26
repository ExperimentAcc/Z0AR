package com.zero.accelerator.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.zero.accelerator.C

object Perms {

    fun shizukuInstalled(ctx: Context): Boolean =
        try {
            ctx.packageManager.getPackageInfo(C.SHIZUKU_MANAGER_PKG, 0)
            true
        } catch (_: Throwable) {
            try {
                ctx.packageManager.getPackageInfo(C.SHIZUKU_PKG, 0)
                true
            } catch (_: Throwable) { false }
        }

    fun accessibilityEnabled(ctx: Context, serviceName: String): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        // Stored as "pkg/cls" entries joined by ':'; a simple contains is robust.
        return enabled.contains(serviceName)
    }

    fun canDrawOverlay(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    fun notificationsEnabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun dndGranted(ctx: Context): Boolean =
        ctx.getSystemService(android.app.NotificationManager::class.java)
            .isNotificationPolicyAccessGranted

    // ---- Settings launchers ----

    fun openAccessibility(ctx: Context) =
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

    fun openOverlay(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 23) {
            ctx.startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    fun openNotifications(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openDnd(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun openShizuku(ctx: Context) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(C.SHIZUKU_MANAGER_PKG)
            ?: ctx.packageManager.getLaunchIntentForPackage(C.SHIZUKU_PKG)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
