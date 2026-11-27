package com.zero.accelerator.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.zero.accelerator.BuildConfig
import com.zero.accelerator.C
import com.zero.accelerator.IOptimizerService
import com.zero.accelerator.model.PerfMode
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Single entry point for everything Shizuku related.
 *
 * Every call is exception-safe: when Shizuku is missing, stopped or not granted the
 * app keeps working in "visual mode" instead of crashing or locking the UI.
 */
object ShizukuGate {

    private var app: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile private var service: IOptimizerService? = null
    @Volatile private var binding = false
    private var inited = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IOptimizerService.Stub.asInterface(binder)
            } else null
            binding = false
            notifyChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
            notifyChanged()
        }
    }

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { notifyChanged() }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        service = null
        binding = false
        notifyChanged()
    }
    private val onPermResult = Shizuku.OnRequestPermissionResultListener { _, _ -> notifyChanged() }

    @Synchronized
    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermResult)
        } catch (_: Throwable) {
        }
    }

    fun addStatusListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeStatusListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        main.post { listeners.forEach { it() } }
    }

    // ---------- state ----------

    fun ping(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun ready(): Boolean = ping() && granted()

    /** @return true when the permission dialog was shown. */
    fun requestPermission(): Boolean = try {
        if (Shizuku.isPreV11() || Shizuku.shouldShowRequestPermissionRationale()) {
            false
        } else {
            Shizuku.requestPermission(C.REQ_SHIZUKU)
            true
        }
    } catch (_: Throwable) {
        false
    }

    // ---------- binding ----------

    private fun args(): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(
                app!!.packageName,
                "com.zero.accelerator.optimizer.OptimizerUserService"
            )
        )
            .processNameSuffix("shizuku")
            .version(BuildConfig.VERSION_CODE)
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)

    /** Blocking. Never call on the main thread. */
    private fun awaitService(timeoutMs: Long): IOptimizerService? {
        service?.let { if (it.asBinder().isBinderAlive) return it }
        if (app == null || !ready()) return null
        if (!binding) {
            binding = true
            try {
                Shizuku.bindUserService(args(), conn)
            } catch (_: Throwable) {
                binding = false
                return null
            }
        }
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (service == null && SystemClock.uptimeMillis() < end) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
        return service
    }

    private fun unbindNow() {
        if (app == null) return
        if (service == null && !binding) return
        try {
            Shizuku.unbindUserService(args(), conn, true)
        } catch (_: Throwable) {
        }
        service = null
        binding = false
    }

    // ---------- operations ----------

    /** Runs the cleanup. [onDone] is called on the main thread with the report (null if Shizuku unavailable). */
    fun optimize(mode: PerfMode, target: String?, excluded: Set<String>, onDone: ((String?) -> Unit)? = null) {
        val ex = excluded.joinToString(",")
        executor.execute {
            var report: String? = null
            try {
                val s = awaitService(3500)
                if (s != null) {
                    s.optimize(mode.level, target ?: "", ex)
                    report = s.lastReport
                }
            } catch (_: Throwable) {
            }
            if (onDone != null) main.post { onDone(report) }
        }
    }

    /** Runs [block] against the privileged service on a background thread. */
    fun withService(block: (IOptimizerService) -> Unit) {
        executor.execute {
            try {
                awaitService(3000)?.let(block)
            } catch (_: Throwable) {
            }
        }
    }

    /** Synchronous telemetry (call from a worker thread). Null when unavailable. */
    fun telemetry(target: String): String? = try {
        service?.takeIf { it.asBinder().isBinderAlive }?.telemetry(target)
    } catch (_: Throwable) {
        null
    }

    /** Restores temporary system changes and tears the privileged process down. */
    fun endSession() {
        executor.execute {
            try {
                service?.restore()
            } catch (_: Throwable) {
            }
            unbindNow()
        }
    }
}
