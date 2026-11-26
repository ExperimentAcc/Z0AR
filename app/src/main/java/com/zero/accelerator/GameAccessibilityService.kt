package com.zero.accelerator

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.overlay.GameOverlay
import com.zero.accelerator.prefs.AppPrefs
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.PackageUtils
import com.zero.accelerator.util.SessionNotif
import com.zero.accelerator.util.WakeLockManager

/**
 * Dormant detector. It listens ONLY for TYPE_WINDOW_STATE_CHANGED events, so it
 * consumes no CPU and holds no timers while idle. When the bound game becomes the
 * foreground window it triggers the optimization routine (via Shizuku) and shows
 * the launch banner + edge control.
 */
class GameAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppPrefs
    private lateinit var overlay: GameOverlay
    private var active = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = AppPrefs(this)
        overlay = GameOverlay(this)
        ShizukuGate.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore our own overlay windows.
        if (pkg == packageName) return

        val target = prefs.targetPackage ?: return
        if (pkg == target) {
            if (!active) onLaunch()
        } else if (active) {
            onExit()
        }
    }

    private fun onLaunch() {
        active = true
        val mode = PerfMode.fromKey(prefs.mode)
        val excluded = buildExcluded()
        overlay.showLaunchBanner(
            mode,
            Build.MODEL.uppercase(),
            prefs.targetPackage ?: "",
            excluded
        )
        ShizukuGate.optimize(mode, prefs.targetPackage, excluded)
        WakeLockManager.acquire(this, mode)
        if (mode == PerfMode.MONSTER) {
            SessionNotif.show(this, mode, prefs.targetPackage)
        }
    }

    private fun onExit() {
        active = false
        overlay.teardown()
        WakeLockManager.release()
        ShizukuGate.unbind()
        SessionNotif.cancel(this)
    }

    private fun buildExcluded(): Set<String> {
        val set = mutableSetOf<String>()
        set += prefs.whitelist
        set += packageName
        set += C.SHIZUKU_PKG
        set += C.SHIZUKU_MANAGER_PKG
        PackageUtils.defaultLauncher(this)?.let { set += it }
        PackageUtils.defaultKeyboard(this)?.let { set += it }
        return set
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::overlay.isInitialized) overlay.teardown()
        WakeLockManager.release()
    }
}
