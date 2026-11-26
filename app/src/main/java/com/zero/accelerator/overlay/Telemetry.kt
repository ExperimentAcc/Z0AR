package com.zero.accelerator.overlay

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.widget.TextView
import com.zero.accelerator.R
import com.zero.accelerator.model.PerfMode
import java.io.File

/**
 * Live HUD telemetry. Data is only collected while the sidebar is open, so the
 * app does nothing when the game is simply being played.
 */
object Telemetry {

    private val handler = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null
    private var frameCb: Choreographer.FrameCallback? = null
    private var frames = 0

    fun start(ctx: android.content.Context, view: View, sessionStart: Long, mode: PerfMode) {
        stop()
        val tvCpu = view.findViewById<TextView>(R.id.tvCpu)
        val tvGpu = view.findViewById<TextView>(R.id.tvGpu)
        val tvFps = view.findViewById<TextView>(R.id.tvFps)
        val tvSession = view.findViewById<TextView>(R.id.tvSession)

        val choreo = Choreographer.getInstance()
        frameCb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frames++
                choreo.postFrameCallback(this)
            }
        }
        choreo.postFrameCallback(frameCb!!)

        tick = object : Runnable {
            override fun run() {
                tvCpu?.text = cpuClock()
                tvGpu?.text = gpuClock()
                tvFps?.text = frames.toString()
                frames = 0
                tvSession?.text = fmt(System.currentTimeMillis() - sessionStart)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(tick!!)
    }

    fun stop() {
        tick?.let { handler.removeCallbacks(it) }
        tick = null
        frameCb?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCb = null
    }

    private fun cpuClock(): String = try {
        val khz = readSysfs("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            ?.trim()?.toLongOrNull()
        if (khz != null && khz > 0) "${khz / 1000} MHz" else "N/A"
    } catch (_: Throwable) {
        "N/A"
    }

    private fun gpuClock(): String {
        val candidates = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/devfreq0/cur_freq",
            "/sys/devices/platform/gpu.0/clock"
        )
        for (p in candidates) {
            val hz = readSysfs(p)?.trim()?.toLongOrNull()
            if (hz != null && hz > 0) return "${hz / 1_000_000} MHz"
        }
        return "N/A"
    }

    private fun readSysfs(path: String): String? = try {
        File(path).bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
        null
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
