package com.zero.accelerator

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.overlay.OverlayController
import com.zero.accelerator.prefs.AppPrefs
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.ui.MainActivity
import com.zero.accelerator.util.PackageUtils
import com.zero.accelerator.util.SessionNotif
import com.zero.accelerator.util.WakeLockManager

/**
 * Detects when the bound game reaches the foreground and hosts the overlay.
 *
 * Idle cost is zero: it only reacts to TYPE_WINDOW_STATE_CHANGED and holds no timers.
 * Transient windows (keyboard, notification shade, volume, popups) never end a session.
 */
class GameAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: GameAccessibilityService? = null
        private const val TAG = "ZeroA11y"
        private const val EXIT_DEBOUNCE_MS = 600L
    }

    private lateinit var prefs: AppPrefs
    private var controller: OverlayController? = null
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var imes: Set<String> = emptySet()
    private val exitRunnable = Runnable { endSession() }

    val isActive: Boolean get() = active

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = AppPrefs(this)
        ShizukuGate.init(this)
        controller = OverlayController(this, prefs)
        imes = imePackages()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handle(event)
        } catch (t: Throwable) {
            Log.w(TAG, "event failed", t)
        }
    }

    private fun handle(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val cls = event.className?.toString().orEmpty()
        val target = prefs.targetPackage ?: return

        if (pkg == target) {
            main.removeCallbacks(exitRunnable)
            if (!active) startSession(false)
            return
        }
        if (!active) return
        if (isTransient(pkg, cls)) return
        main.removeCallbacks(exitRunnable)
        main.postDelayed(exitRunnable, EXIT_DEBOUNCE_MS)
    }

    /** Windows that appear on top of a game without the game having been left. */
    private fun isTransient(pkg: String, cls: String): Boolean {
        if (pkg == C.SYSTEMUI || pkg == "android") return true
        if (pkg in imes) return true
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.") ||
            cls.startsWith("androidx.")
        ) return true
        return false
    }

    private fun imePackages(): Set<String> = try {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.enabledInputMethodList.map { it.packageName }.toSet()
    } catch (_: Throwable) {
        emptySet()
    }

    // ---------- session ----------

    private fun startSession(demo: Boolean) {
        val target = prefs.targetPackage ?: if (demo) packageName else return
        val mode = PerfMode.fromKey(prefs.mode)
        active = true
        imes = imePackages()
        WakeLockManager.acquire(this, mode)
        if (mode == PerfMode.MONSTER) SessionNotif.show(this, mode, target)
        controller?.start(target, mode, buildExcluded(target), demo)
    }

    private fun endSession() {
        if (!active) return
        active = false
        main.removeCallbacks(exitRunnable)
        controller?.stop()
        WakeLockManager.release()
        SessionNotif.cancel(this)
        ShizukuGate.endSession()
    }

    private fun buildExcluded(target: String): Set<String> {
        val set = mutableSetOf<String>()
        set += prefs.whitelist
        set += packageName
        set += target
        set += C.SHIZUKU_PKG
        set += C.SHIZUKU_MANAGER_PKG
        set += imes
        PackageUtils.defaultLauncher(this)?.let { set += it }
        PackageUtils.defaultKeyboard(this)?.let { set += it }
        return set
    }

    // ---------- called by the overlay / app ----------

    fun onModeChanged(m: PerfMode) {
        WakeLockManager.acquire(this, m)
        if (m == PerfMode.MONSTER) SessionNotif.show(this, m, prefs.targetPackage)
        else SessionNotif.cancel(this)
    }

    fun screenshot() {
        if (Build.VERSION.SDK_INT >= 28) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            Toast.makeText(this, "Screenshot needs Android 9+", Toast.LENGTH_SHORT).show()
        }
    }

    fun openApp() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
        }
    }

    /** Shows splash + handle without needing the game (used by the Preview button). */
    fun preview() {
        main.post {
            if (active) endSession()
            startSession(true)
        }
    }

    fun stopPreview() {
        main.post { endSession() }
    }

    // ---------- framework ----------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigChanged()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        endSession()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        endSession()
        instance = null
        super.onDestroy()
    }
}
