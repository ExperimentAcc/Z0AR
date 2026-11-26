package com.zero.accelerator.prefs

import android.content.Context
import android.content.SharedPreferences
import com.zero.accelerator.model.PerfMode

/** Tiny SharedPreferences wrapper — no third-party JSON libs. */
class AppPrefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences("zero_prefs", Context.MODE_PRIVATE)

    var targetPackage: String?
        get() = sp.getString(KEY_TARGET, null)
        set(v) = sp.edit().putString(KEY_TARGET, v).apply()

    var mode: String
        get() = sp.getString(KEY_MODE, PerfMode.BALANCED.key)!!
        set(v) = sp.edit().putString(KEY_MODE, v).apply()

    var whitelist: Set<String>
        get() = sp.getString(KEY_WL, "")!!
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        set(v) = sp.edit().putString(KEY_WL, v.joinToString(",")).apply()

    fun addWhitelist(pkg: String) {
        if (pkg.isBlank()) return
        whitelist = whitelist + pkg
    }

    fun removeWhitelist(pkg: String) {
        whitelist = whitelist - pkg
    }

    companion object {
        private const val KEY_TARGET = "target"
        private const val KEY_MODE = "mode"
        private const val KEY_WL = "whitelist"
    }
}
