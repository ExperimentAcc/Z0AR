package com.zero.accelerator.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.Sysfs
import java.util.concurrent.Executors

class Sample(
    val cpuKhz: Long,
    val cpuMaxKhz: Long,
    val gpuMhz: Int,
    val gpuMaxMhz: Int,
    val fps: Int,
    val battery: Int
)

/**
 * 1 Hz sampler. Only runs while the panel is open, so the idle footprint stays zero.
 * Data comes from the Shizuku shell process (apps are blocked from sysfs by SELinux),
 * with a direct-read fallback.
 */
class TelemetrySampler(private val ctx: Context, private val target: String) {

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var inFlight = false
    private var peakGpu = 0
    private var callback: ((Sample) -> Unit)? = null

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            poll()
            main.postDelayed(this, 1000)
        }
    }

    fun start(cb: (Sample) -> Unit) {
        callback = cb
        running = true
        main.post(loop)
    }

    fun stop() {
        running = false
        main.removeCallbacks(loop)
        callback = null
        exec.shutdownNow()
    }

    private fun poll() {
        if (inFlight) return
        inFlight = true
        try {
            exec.execute {
                val s = try {
                    read()
                } catch (_: Throwable) {
                    null
                }
                main.post {
                    inFlight = false
                    if (running && s != null) callback?.invoke(s)
                }
            }
        } catch (_: Throwable) {
            inFlight = false
        }
    }

    private fun read(): Sample {
        var cpu = 0L
        var cpuMax = 0L
        var gpu = 0
        var gpuMax = 0
        var fps = -1

        val raw = ShizukuGate.telemetry(target)
        if (raw != null) {
            val p = raw.split('|')
            cpu = p.getOrNull(0)?.toLongOrNull() ?: 0L
            cpuMax = p.getOrNull(1)?.toLongOrNull() ?: 0L
            gpu = p.getOrNull(2)?.toIntOrNull() ?: 0
            gpuMax = p.getOrNull(3)?.toIntOrNull() ?: 0
            fps = p.getOrNull(4)?.toIntOrNull() ?: -1
        } else {
            val c = Sysfs.cpu()
            cpu = c[0]
            cpuMax = c[1]
            val g = Sysfs.gpu()
            gpu = g[0]
            gpuMax = g[1]
        }
        if (gpu > peakGpu) peakGpu = gpu
        if (gpuMax <= 0) gpuMax = peakGpu
        return Sample(cpu, cpuMax, gpu, gpuMax, fps, battery())
    }

    private fun battery(): Int = try {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) -1 else level * 100 / scale
    } catch (_: Throwable) {
        -1
    }
}
