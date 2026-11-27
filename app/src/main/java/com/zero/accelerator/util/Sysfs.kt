package com.zero.accelerator.util

import java.io.File

/**
 * Best-effort clock readers. Apps are usually blocked by SELinux from these files,
 * but the Shizuku (shell) process can read them, so the privileged service uses this.
 */
object Sysfs {

    private val CPU_RE = Regex("cpu[0-9]+")

    private fun readLong(f: File): Long? = try {
        f.readText().trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
    } catch (_: Throwable) {
        null
    }

    private fun toMhz(v: Long): Int = when {
        v >= 10_000_000L -> (v / 1_000_000L).toInt() // Hz
        v >= 10_000L -> (v / 1_000L).toInt()         // kHz
        else -> v.toInt()                            // MHz
    }

    /** Returns [highest current kHz, highest hardware max kHz]. */
    fun cpu(): LongArray {
        var cur = 0L
        var max = 0L
        val dirs = File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(CPU_RE) }
            ?: return longArrayOf(0L, 0L)
        for (d in dirs) {
            readLong(File(d, "cpufreq/scaling_cur_freq"))?.let { if (it > cur) cur = it }
            readLong(File(d, "cpufreq/cpuinfo_max_freq"))?.let { if (it > max) max = it }
        }
        return longArrayOf(cur, max)
    }

    /** Returns [current MHz, max MHz]; zeros when unreadable. */
    fun gpu(): IntArray {
        val cur = firstPositive(
            listOf(
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/kernel/gpu/gpu_clock"
            ),
            "cur_freq"
        )
        val max = firstPositive(
            listOf(
                "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
                "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
                "/sys/kernel/gpu/gpu_max_clock"
            ),
            "max_freq"
        )
        return intArrayOf(cur, max)
    }

    private fun firstPositive(fixed: List<String>, devfreqFile: String): Int {
        for (p in fixed) {
            val v = readLong(File(p))
            if (v != null && v > 0) return toMhz(v)
        }
        val dirs = File("/sys/class/devfreq").listFiles() ?: return 0
        for (d in dirs) {
            val n = d.name.lowercase()
            if (n.contains("mali") || n.contains("gpu") || n.contains("kgsl")) {
                val v = readLong(File(d, devfreqFile))
                if (v != null && v > 0) return toMhz(v)
            }
        }
        return 0
    }
}
