package com.zero.accelerator.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.zero.accelerator.BuildConfig
import com.zero.accelerator.C
import com.zero.accelerator.IOptimizerService
import com.zero.accelerator.model.PerfMode
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Single entry point for everything Shizuku-related.
 *
 *  - Tracks binder / permission state and notifies listeners.
 *  - Binds the privileged UserService on demand and exposes a typed binder.
 *  - Routes optimization requests onto a background thread.
 */
object ShizukuGate {

    private var appCtx: Context? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = mutableListOf<() -> Unit>()

    @Volatile private var binder: IOptimizerService? = null
    @Volatile private var bound = false
    private var inited = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = IOptimizerService.Stub.asInterface(service)
            bound = binder != null
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            bound = false
            refresh()
        }
    }

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        bound = false
        binder = null
        refresh()
    }
    private val onPermResult = Shizuku.OnRequestPermissionResultListener { _, grant ->
        if (grant == PackageManager.PERMISSION_GRANTED) bind()
        refresh()
    }

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        appCtx = ctx.applicationContext
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermResult)
        refresh()
    }

    fun addStatusListener(l: () -> Unit) {
        if (l !in listeners) listeners.add(l)
    }

    fun removeStatusListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun refresh() {
        listeners.forEach { it() }
    }

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

    fun requestPermission() {
        if (Shizuku.isPreV11()) return
        if (Shizuku.shouldShowRequestPermissionRationale()) return
        Shizuku.requestPermission(C.REQ_SHIZUKU)
    }

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                appCtx!!.packageName,
                "com.zero.accelerator.optimizer.OptimizerUserService"
            )
        )
            .processNameSuffix("shizuku")
            .version(BuildConfig.VERSION_CODE)
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
    }

    fun bind() {
        if (!ready() || bound) return
        try {
            Shizuku.bindUserService(args, conn)
        } catch (_: Throwable) {
            // Permission not granted or Shizuku busy; caller will retry on next launch.
        }
    }

    fun unbind() {
        if (!bound) return
        try {
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: Throwable) {
        }
        binder = null
        bound = false
    }

    fun optimize(mode: PerfMode, target: String?, excluded: Set<String>) {
        val m = when (mode) {
            PerfMode.LOW -> 0
            PerfMode.BALANCED -> 1
            PerfMode.MONSTER -> 2
        }
        val ex = (excluded + requireCtx().packageName).joinToString(",")
        executor.execute {
            if (!bound) bind()
            var tries = 0
            while (binder == null && tries < 25) {
                Thread.sleep(100)
                tries++
            }
            binder?.optimize(m, target, ex)
        }
    }

    fun lastReport(): String? = binder?.lastReport

    private fun requireCtx(): Context = appCtx!!
}
