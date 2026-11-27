package com.zero.accelerator.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.zero.accelerator.C

object Perms {

    fun shizukuInstalled(ctx: Context): Boolean {
        val pm = ctx.packageManager
        return try {
            pm.getPackageInfo(C.SHIZUKU_MANAGER_PKG, 0); true
        } catch (_: Throwable) {
            try {
                pm.getPackageInfo(C.SHIZUKU_PKG, 0); true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun accessibilityEnabled(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
    } catch (_: Throwable) {
        false
    }

    fun notificationsEnabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun batteryUnrestricted(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Throwable) {
        false
    }

    private fun launch(ctx: Context, i: Intent) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }

    fun openAccessibility(ctx: Context) = launch(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    )

    fun openNotifications(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    )

    fun openDnd(ctx: Context) = launch(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun openBattery(ctx: Context) = launch(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openShizuku(ctx: Context) {
        val pm = ctx.packageManager
        val i = pm.getLaunchIntentForPackage(C.SHIZUKU_MANAGER_PKG)
            ?: pm.getLaunchIntentForPackage(C.SHIZUKU_PKG)
        if (i != null) launch(ctx, i)
        else launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
    }

    fun launchGame(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch(ctx, i)
        return true
    }
}
