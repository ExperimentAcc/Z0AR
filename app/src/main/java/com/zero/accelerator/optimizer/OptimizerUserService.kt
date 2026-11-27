package com.zero.accelerator.optimizer

import android.content.Context
import android.os.SystemClock
import com.zero.accelerator.IOptimizerService
import com.zero.accelerator.util.Sysfs
import kotlin.math.roundToInt

/**
 * Runs INSIDE the Shizuku-privileged process (shell uid 2000 / root uid 0).
 * Shizuku instantiates this class by name. It is a plain AIDL Stub, not a Service.
 */
class OptimizerUserService() : IOptimizerService.Stub() {

    @Suppress("unused")
    constructor(ctx: Context) : this()

    @Volatile private var lastReport: String = ""

    private var layer: String? = null
    private var layerAt = 0L

    private var brightnessSaved: Int? = null
    private var refreshTouched = false
    private var perfFixed = false

    override fun destroy() {
        System.exit(0)
    }

    // ---------- optimize ----------

    override fun optimize(mode: Int, targetPackage: String?, excludedPackages: String?) {
        val target = targetPackage ?: ""
        val excluded = (excludedPackages ?: "")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        excluded += target
        excluded += "com.zero.accelerator"
        excluded += "moe.shizuku.privileged.api"
        excluded += "moe.shizuku.manager"

        // Never kill apps that currently host an accessibility service.
        val a11y = sh("settings get secure enabled_accessibility_services").trim()
        if (a11y.isNotEmpty() && a11y != "null") {
            a11y.split(':').forEach { excluded += it.substringBefore('/').trim() }
        }

        val sb = StringBuilder()
        val t0 = SystemClock.elapsedRealtime()
        val safe = Regex("^[A-Za-z0-9._]+$")

        val victims = sh("pm list packages -3")
            .lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in excluded && safe.matches(it) }
            .toList()

        // Stop in parallel batches: spawning one process per app sequentially is slow.
        for (chunk in victims.chunked(12)) {
            sh(chunk.joinToString(" ") { "cmd activity force-stop $it &" } + " wait")
        }
        sb.appendLine("Force-stopped ${victims.size} background apps")

        if (mode >= 1) {
            sh("pm trim-caches 2000000000")
            sb.appendLine("Trimmed system caches")
            if (target.isNotEmpty() && safe.matches(target)) {
                sh("am set-standby-bucket $target active")
                sb.appendLine("Game standby bucket = active")
            }
        }

        if (mode >= 2) {
            sh("settings put global low_power 0")
            setFixedPerformance(true)
            if (target.isNotEmpty() && safe.matches(target)) {
                // Syntax differs between Android 12 and 13+, one of them works.
                sh("cmd game set --mode performance --user 0 $target || " +
                    "cmd game mode --user 0 performance $target")
            }
            sh(
                "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; " +
                    "do echo performance > \$f 2>/dev/null; done"
            )
            sb.appendLine("Performance profile applied")
        }

        sb.appendLine("Completed in ${SystemClock.elapsedRealtime() - t0}ms")
        lastReport = sb.toString()
    }

    override fun getLastReport(): String = lastReport

    // ---------- telemetry ----------

    override fun telemetry(targetPackage: String?): String {
        val cpu = Sysfs.cpu()
        val gpu = Sysfs.gpu()
        val fps = try {
            fps(targetPackage ?: "")
        } catch (_: Throwable) {
            -1
        }
        return "${cpu[0]}|${cpu[1]}|${gpu[0]}|${gpu[1]}|$fps"
    }

    /** Real game FPS from SurfaceFlinger frame timestamps. -1 when it cannot be measured. */
    private fun fps(pkg: String): Int {
        if (pkg.isEmpty()) return -1
        val now = SystemClock.elapsedRealtime()
        if (layer == null || now - layerAt > 15_000) {
            layer = findLayer(pkg)
            layerAt = now
        }
        val l = layer ?: return -1
        val out = exec("dumpsys", "SurfaceFlinger", "--latency", l)
        val ts = ArrayList<Long>()
        for (line in out.lineSequence().drop(1)) {
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 3) continue
            val t = p[1].toLongOrNull() ?: continue
            if (t <= 0L || t == Long.MAX_VALUE) continue
            ts.add(t)
        }
        if (ts.size < 2) {
            layer = null
            return -1
        }
        val last = ts.maxOrNull() ?: return -1
        if (System.nanoTime() - last > 2_000_000_000L) return 0
        val win = ts.filter { it >= last - 1_000_000_000L }
        if (win.size < 2) return 0
        val span = (win.maxOrNull() ?: 0L) - (win.minOrNull() ?: 0L)
        if (span <= 0L) return 0
        return ((win.size - 1) * 1_000_000_000.0 / span).roundToInt()
    }

    private fun findLayer(pkg: String): String? {
        val all = exec("dumpsys", "SurfaceFlinger", "--list")
            .lineSequence().map { it.trim() }.filter { it.contains(pkg) }.toList()
        return all.lastOrNull { it.startsWith("SurfaceView") && it.contains("BLAST") }
            ?: all.lastOrNull { it.startsWith("SurfaceView") }
            ?: all.lastOrNull { !it.startsWith("Background") && !it.startsWith("Bounds") }
    }

    // ---------- toggles ----------

    override fun setRefreshRate(hz: Int) {
        refreshTouched = true
        if (hz <= 0) {
            sh("settings delete system min_refresh_rate; settings delete system peak_refresh_rate")
        } else {
            sh("settings put system min_refresh_rate $hz; settings put system peak_refresh_rate $hz")
        }
    }

    override fun setBrightnessLock(on: Boolean) {
        if (on) {
            if (brightnessSaved == null) {
                brightnessSaved = sh("settings get system screen_brightness_mode").trim().toIntOrNull() ?: 1
            }
            sh("settings put system screen_brightness_mode 0")
        } else {
            val v = brightnessSaved ?: 1
            sh("settings put system screen_brightness_mode $v")
            brightnessSaved = null
        }
    }

    override fun setNetwork(which: String?, on: Boolean) {
        val state = if (on) "enable" else "disable"
        when (which) {
            "wifi" -> sh("svc wifi $state")
            "data" -> sh("svc data $state")
        }
    }

    override fun setFixedPerformance(on: Boolean) {
        perfFixed = perfFixed || on
        sh("cmd power set-fixed-performance-mode-enabled $on")
        if (!on) perfFixed = false
    }

    override fun restore() {
        if (perfFixed) setFixedPerformance(false)
        brightnessSaved?.let {
            sh("settings put system screen_brightness_mode $it")
            brightnessSaved = null
        }
        if (refreshTouched) {
            sh("settings delete system min_refresh_rate; settings delete system peak_refresh_rate")
            refreshTouched = false
        }
    }

    // ---------- shell ----------

    private fun sh(cmd: String): String = exec("sh", "-c", cmd)

    private fun exec(vararg args: String): String = try {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out
    } catch (_: Throwable) {
        ""
    }
}
