package com.zero.accelerator.overlay

import android.app.NotificationManager
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.zero.accelerator.GameAccessibilityService
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.prefs.AppPrefs
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.DndUtil
import com.zero.accelerator.util.PackageUtils
import com.zero.accelerator.util.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Owns every overlay window: boot splash -> edge handle -> slide-in panel.
 *
 * Windows use TYPE_ACCESSIBILITY_OVERLAY, which an enabled accessibility service may
 * add WITHOUT the "display over other apps" permission. All add/remove calls are
 * guarded so an overlay failure can never crash the service.
 */
class OverlayController(
    private val svc: GameAccessibilityService,
    private val prefs: AppPrefs
) : PanelActions {

    private val tag = "ZeroOverlay"
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var splash: SplashView? = null
    private var handle: HandleView? = null
    private var handleLp: WindowManager.LayoutParams? = null
    private var panel: PanelView? = null
    private var sampler: TelemetrySampler? = null

    private var running = false
    private var demo = false
    private var target = ""
    private var mode = PerfMode.BALANCED
    private var excluded: Set<String> = emptySet()
    private var startedAt = 0L

    // transient session toggles
    private var keepAwake = true
    private var brightLock = false
    private var perfFixed = false
    private var hz = 0
    private var dndPrev: Int? = null
    private var mutedByUs = false

    // splash state
    private var splashStart = 0L
    private var optimizeDone = false
    private var report: String? = null
    private var stepIdx = 0
    private val steps = arrayOf(
        "PURGING RAM", "TRIMMING CACHES", "ACTIVATING STANDBY", "BOOSTING CLOCKS", "LOCKING FOCUS"
    )

    private val splashTick = object : Runnable {
        override fun run() {
            val v = splash ?: return
            val el = SystemClock.uptimeMillis() - splashStart
            if ((optimizeDone && el >= MIN_SPLASH_MS) || el >= MAX_SPLASH_MS) {
                finishSplash(v)
                return
            }
            v.setStep(steps[stepIdx % steps.size])
            stepIdx++
            v.setProgress(if (optimizeDone) 0.97f else min(0.9f, el / 3200f))
            main.postDelayed(this, 320)
        }
    }

    private val fadeRunnable = Runnable {
        handle?.animate()?.alpha(0.4f)?.setDuration(500)?.start()
    }

    // ---------- lifecycle ----------

    fun start(target: String, mode: PerfMode, excluded: Set<String>, demo: Boolean) {
        stop()
        running = true
        this.demo = demo
        this.target = target
        this.mode = mode
        this.excluded = excluded
        this.keepAwake = prefs.keepAwake
        this.startedAt = SystemClock.elapsedRealtime()
        optimizeDone = false
        report = null
        stepIdx = 0

        if (demo) {
            main.postDelayed({ optimizeDone = true }, 1200)
        } else {
            ShizukuGate.optimize(mode, target, excluded) { r ->
                report = r
                optimizeDone = true
            }
        }

        if (prefs.splash) showSplash() else showHandle()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        sampler?.stop()
        sampler = null
        safeRemove(splash); splash = null
        safeRemove(panel); panel = null
        safeRemove(handle); handle = null
        handleLp = null

        // give back anything the user toggled during the session
        dndPrev?.let { DndUtil.set(svc, it) }
        dndPrev = null
        if (mutedByUs) {
            audio()?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            mutedByUs = false
        }
        brightLock = false
        perfFixed = false
        hz = 0
    }

    fun onConfigChanged() {
        if (!running) return
        safeRemove(panel); panel = null
        sampler?.stop(); sampler = null
        handle?.visibility = View.VISIBLE
        val lp = handleLp
        val h = handle
        if (lp != null && h != null) {
            lp.y = svc.dp(prefs.handleY)
            try { wm.updateViewLayout(h, lp) } catch (_: Throwable) {}
        }
    }

    // ---------- windows ----------

    private fun lp(w: Int, h: Int, flags: Int, gravity: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = gravity
        p.title = "zero-overlay"
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun safeAdd(v: View, p: WindowManager.LayoutParams): Boolean = try {
        wm.addView(v, p)
        true
    } catch (t: Throwable) {
        Log.w(tag, "addView failed", t)
        false
    }

    private fun safeRemove(v: View?) {
        if (v == null) return
        try { wm.removeViewImmediate(v) } catch (_: Throwable) {}
    }

    // ---------- splash ----------

    private fun showSplash() {
        val icon = try { svc.packageManager.getApplicationIcon(target) } catch (_: Throwable) { null }
        val v = SplashView(svc, mode, gameLabel(), icon, Build.MODEL ?: "DEVICE")
        val p = lp(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            Gravity.TOP or Gravity.START
        )
        if (!safeAdd(v, p)) {
            showHandle()
            return
        }
        splash = v
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(200).start()
        splashStart = SystemClock.uptimeMillis()
        main.post(splashTick)
    }

    private fun finishSplash(v: SplashView) {
        splash = null
        v.setProgress(1f)
        v.setStep(
            if (report != null || demo)
                (report?.lineSequence()?.firstOrNull()?.uppercase() ?: "READY")
            else "SHIZUKU OFFLINE - VISUAL MODE"
        )
        showHandle()
        v.animate().alpha(0f).setStartDelay(350).setDuration(300).withEndAction {
            safeRemove(v)
        }.start()
        main.postDelayed({ safeRemove(v) }, 1200)
    }

    // ---------- handle ----------

    private fun showHandle() {
        if (!running || handle != null) return
        val v = HandleView(svc) { openPanel() }
        v.onTouched = {
            v.animate().cancel()
            v.alpha = 1f
            scheduleFade()
        }
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (keepAwake) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val p = lp(svc.dp(28), svc.dp(96), flags, Gravity.TOP or Gravity.START)
        p.x = 0
        p.y = svc.dp(prefs.handleY)
        if (!safeAdd(v, p)) return
        handle = v
        handleLp = p
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(250).start()
        scheduleFade()
    }

    private fun scheduleFade() {
        main.removeCallbacks(fadeRunnable)
        main.postDelayed(fadeRunnable, 3000)
    }

    private fun applyKeepAwake() {
        val p = handleLp ?: return
        val h = handle ?: return
        p.flags = if (keepAwake) p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        else p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        try { wm.updateViewLayout(h, p) } catch (_: Throwable) {}
    }

    // ---------- panel ----------

    private fun hzOptions(): List<Int> = try {
        val dm = svc.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val set = sortedSetOf<Int>()
        for (m in dm.getDisplay(Display.DEFAULT_DISPLAY).supportedModes) set.add(m.refreshRate.roundToInt())
        val l = set.filter { it >= 50 }
        if (l.isEmpty()) listOf(60) else l
    } catch (_: Throwable) {
        listOf(60)
    }

    private fun openPanel() {
        if (!running || panel != null) return
        val dm = svc.resources.displayMetrics
        val landscape = dm.widthPixels > dm.heightPixels
        val cardW = if (landscape) {
            (dm.widthPixels * 0.46f).toInt().coerceIn(svc.dp(340), svc.dp(480))
        } else {
            (dm.widthPixels * 0.92f).toInt()
        }
        val v = PanelView(svc, this, cardW, hzOptions())
        val p = lp(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            Gravity.TOP or Gravity.START
        )
        if (!safeAdd(v, p)) return
        panel = v
        handle?.visibility = View.INVISIBLE
        v.refreshStates()
        v.updateDuration(SystemClock.elapsedRealtime() - startedAt)
        v.animateIn()
        sampler = TelemetrySampler(svc, target).also { s ->
            s.start { sample -> panel?.onSample(sample, SystemClock.elapsedRealtime() - startedAt) }
        }
    }

    override fun onClose() {
        val v = panel ?: return
        panel = null
        sampler?.stop()
        sampler = null
        handle?.visibility = View.VISIBLE
        scheduleFade()
        v.animateOut { safeRemove(v) }
        main.postDelayed({ safeRemove(v) }, 500)
    }

    // ---------- PanelActions ----------

    override fun currentMode(): PerfMode = mode

    override fun onModeSelected(m: PerfMode) {
        if (m == mode) return
        mode = m
        prefs.mode = m.key
        svc.onModeChanged(m)
        panel?.refreshStates()
        runOptimize(m)
    }

    override fun onRamClean() = runOptimize(mode)

    private fun runOptimize(m: PerfMode) {
        if (!ShizukuGate.ready()) {
            toast("Shizuku required for cleaning")
            return
        }
        toast("Cleaning...")
        ShizukuGate.optimize(m, target, excluded) { r ->
            toast(r?.lineSequence()?.firstOrNull() ?: "Done")
        }
    }

    override fun refreshHz(): Int = hz

    override fun onRefreshSelected(hz: Int) {
        if (!requireShizuku()) return
        this.hz = hz
        ShizukuGate.withService { it.setRefreshRate(hz) }
        panel?.refreshStates()
    }

    private fun transport(t: Int): Boolean = try {
        val cm = svc.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(t) == true
    } catch (_: Throwable) {
        false
    }

    override fun wifiActive(): Boolean = transport(NetworkCapabilities.TRANSPORT_WIFI)

    override fun mobileActive(): Boolean = transport(NetworkCapabilities.TRANSPORT_CELLULAR)

    override fun onNetwork(wifi: Boolean) {
        if (!requireShizuku()) return
        ShizukuGate.withService { s ->
            if (wifi) {
                s.setNetwork("wifi", true)
            } else {
                s.setNetwork("data", true)
                s.setNetwork("wifi", false)
            }
        }
        main.postDelayed({ panel?.refreshStates() }, 2000)
    }

    private fun audio(): AudioManager? =
        svc.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun tileActive(id: String): Boolean = when (id) {
        "dnd" -> DndUtil.active(svc)
        "bright" -> brightLock
        "awake" -> keepAwake
        "perf" -> perfFixed
        "mute" -> try { audio()?.isStreamMute(AudioManager.STREAM_MUSIC) == true } catch (_: Throwable) { false }
        else -> false
    }

    override fun onTile(id: String) {
        when (id) {
            "dnd" -> {
                if (!DndUtil.granted(svc)) {
                    toast("Grant Do Not Disturb access in ZERO settings")
                    svc.openApp()
                    return
                }
                val cur = DndUtil.filter(svc)
                if (DndUtil.active(svc)) {
                    DndUtil.set(svc, dndPrev ?: NotificationManager.INTERRUPTION_FILTER_ALL)
                    dndPrev = null
                } else {
                    if (dndPrev == null) dndPrev = cur
                    DndUtil.set(svc, NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            }
            "bright" -> {
                if (!requireShizuku()) return
                brightLock = !brightLock
                val on = brightLock
                ShizukuGate.withService { it.setBrightnessLock(on) }
            }
            "awake" -> {
                keepAwake = !keepAwake
                applyKeepAwake()
            }
            "perf" -> {
                if (!requireShizuku()) return
                perfFixed = !perfFixed
                val on = perfFixed
                ShizukuGate.withService { it.setFixedPerformance(on) }
            }
            "mute" -> {
                val a = audio() ?: return
                val wasMuted = try { a.isStreamMute(AudioManager.STREAM_MUSIC) } catch (_: Throwable) { false }
                a.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (wasMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE, 0
                )
                mutedByUs = !wasMuted
            }
            "ram" -> onRamClean()
            "shot" -> {
                onClose()
                main.postDelayed({ svc.screenshot() }, 450)
            }
            "settings" -> {
                onClose()
                svc.openApp()
            }
        }
    }

    override fun gameLabel(): String =
        if (target.isEmpty()) "Game" else PackageUtils.label(svc, target)

    // ---------- helpers ----------

    private fun requireShizuku(): Boolean {
        if (ShizukuGate.ready()) return true
        toast("Needs Shizuku: start it and grant access")
        return false
    }

    private fun toast(s: String) {
        try { Toast.makeText(svc, s, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
    }

    companion object {
        private const val MIN_SPLASH_MS = 1600L
        private const val MAX_SPLASH_MS = 4500L
    }
}
