package com.zero.accelerator.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

data class AppInfo(val label: String, val packageName: String)

object PackageUtils {

    /** Launchable apps, sorted by label. */
    fun launchableApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(
            ctx.packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (_: Throwable) {
        pkg
    }

    fun defaultLauncher(ctx: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val ri = ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName
    }

    fun defaultKeyboard(ctx: Context): String? {
        val s = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return null
        return s.substringBefore('/').takeIf { it.isNotBlank() }
    }
}
