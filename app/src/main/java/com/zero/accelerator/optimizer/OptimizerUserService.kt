package com.zero.accelerator.optimizer

import android.content.Context
import android.os.SystemClock
import com.zero.accelerator.IOptimizerService
import java.io.BufferedReader

/**
 * Runs INSIDE the Shizuku-privileged process (shell uid 2000 / root uid 0).
 *
 * Shizuku instantiates this class by name and exposes its IBinder back to the
 * app. It is NOT an android.app.Service — it is a plain AIDL Stub.
 *
 * MODE MAP
 *   0 LOW      : force-stop heavy background apps only.
 *   1 BALANCED : full RAM purge + cache trim + active standby bucket.
 *   2 MONSTER  : extensive purge + performance governor + power-mode 0.
 */
class OptimizerUserService : IOptimizerService.Stub() {

    // Shizuku v13 may hand us a Context; the no-arg constructor is required.
    @Suppress("unused")
    constructor() : super()

    @Suppress("unused")
    constructor(ctx: Context) : super()

    @Volatile
    private var lastReport: String = ""

    override fun destroy() {
        // Shizuku asks the process to die.
        System.exit(0)
    }

    override fun optimize(mode: Int, targetPackage: String?, excludedPackages: String?) {
        val target = targetPackage ?: ""
        val excluded = (excludedPackages ?: "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        excluded += target
        excluded += "com.zero.accelerator"
        excluded += "moe.shizuku.privileged.api"
        excluded += "moe.shizuku.manager"

        val sb = StringBuilder()
        val t0 = SystemClock.elapsedRealtime()

        // 1) RAM purge: force-stop every third-party app that is not protected.
        val thirdParty = runCmd("pm list packages -3")
            .lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in excluded }
            .toList()

        var stopped = 0
        for (pkg in thirdParty) {
            runCmd("am force-stop $pkg")
            stopped++
        }
        sb.appendLine("Force-stopped $stopped background apps")

        if (mode >= 1) {
            runCmd("pm trim-caches 2000000000")
            sb.appendLine("Trimmed system caches")
            if (target.isNotEmpty()) {
                runCmd("am set-standby-bucket $target active")
                sb.appendLine("Target standby bucket = active")
            }
        }

        if (mode >= 2) {
            // Disable battery saver and push a performance profile (best effort).
            runCmd("settings put global low_power 0")
            runCmd(
                "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; " +
                    "do echo performance > \$f 2>/dev/null; done"
            )
            sb.appendLine("Performance governor + power mode 0")
        }

        val dt = SystemClock.elapsedRealtime() - t0
        sb.appendLine("Completed in ${dt}ms")
        lastReport = sb.toString()
    }

    override fun getLastReport(): String = lastReport

    private fun runCmd(cmd: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().use(BufferedReader::readText)
            p.waitFor()
            out
        } catch (_: Throwable) {
            ""
        }
    }
}
