package com.zero.accelerator

import android.app.Application
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.SessionNotif

class ZeroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuGate.init(this)
        SessionNotif.ensureChannel(this)
    }
}
