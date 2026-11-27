package com.zero.accelerator.prefs

import android.content.Context
import android.content.SharedPreferences
import com.zero.accelerator.model.PerfMode

/** Tiny SharedPreferences wrapper. */
class AppPrefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("zero_prefs", Context.MODE_PRIVATE)

    var targetPackage: String?
        get() = sp.getString(KEY_TARGET, null)?.takeIf { it.isNotEmpty() }
        set(v) {
            sp.edit().putString(KEY_TARGET, v).apply()
        }

    var mode: String
        get() = sp.getString(KEY_MODE, PerfMode.BALANCED.key) ?: PerfMode.BALANCED.key
        set(v) {
            sp.edit().putString(KEY_MODE, v).apply()
        }

    var whitelist: Set<String>
        get() = (sp.getString(KEY_WL, "") ?: "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        set(v) {
            sp.edit().putString(KEY_WL, v.joinToString(",")).apply()
        }

    /** Show the boot splash when the game starts. */
    var splash: Boolean
        get() = sp.getBoolean(KEY_SPLASH, true)
        set(v) {
            sp.edit().putBoolean(KEY_SPLASH, v).apply()
        }

    /** Keep the screen awake while the game runs. */
    var keepAwake: Boolean
        get() = sp.getBoolean(KEY_AWAKE, true)
        set(v) {
            sp.edit().putBoolean(KEY_AWAKE, v).apply()
        }

    /** Distance (dp) of the left-edge handle from the top of the screen. */
    var handleY: Int
        get() = sp.getInt(KEY_HANDLE_Y, 64)
        set(v) {
            sp.edit().putInt(KEY_HANDLE_Y, v).apply()
        }

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
        private const val KEY_SPLASH = "splash"
        private const val KEY_AWAKE = "awake"
        private const val KEY_HANDLE_Y = "handle_y"
    }
}
