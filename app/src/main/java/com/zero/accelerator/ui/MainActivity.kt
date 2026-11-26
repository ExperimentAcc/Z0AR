package com.zero.accelerator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zero.accelerator.C
import com.zero.accelerator.R
import com.zero.accelerator.databinding.ActivityMainBinding
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.prefs.AppPrefs
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.AppInfo
import com.zero.accelerator.util.Drawables
import com.zero.accelerator.util.PackageUtils
import com.zero.accelerator.util.Perms
import com.zero.accelerator.util.color

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPrefs

    private val serviceName = "GameAccessibilityService"
    private val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
    private var lockDialog: AlertDialog? = null

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    private val statusListener = { refreshShizuku() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        ShizukuGate.init(this)
        ShizukuGate.addStatusListener(statusListener)

        setupRows()
        setupTarget()
        setupModes()
        setupWhitelist()
    }

    override fun onDestroy() {
        ShizukuGate.removeStatusListener(statusListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshShizuku()
        refreshPermissions()
        refreshTarget()
        refreshMode()
        refreshWhitelist()
    }

    // ---------- Shizuku ----------

    private fun refreshShizuku() {
        val installed = Perms.shizukuInstalled(this)
        val ping = ShizukuGate.ping()
        val granted = ShizukuGate.granted()
        val status: String
        val button: String
        val action: () -> Unit

        when {
            !installed -> {
                status = getString(R.string.shizuku_not_installed)
                button = getString(R.string.lock_install)
                action = { Perms.openShizuku(this) }
            }
            !ping -> {
                status = getString(R.string.shizuku_not_running)
                button = getString(R.string.shizuku_connect)
                action = { Perms.openShizuku(this) }
            }
            !granted -> {
                status = getString(R.string.shizuku_denied)
                button = getString(R.string.shizuku_connect)
                action = { ShizukuGate.requestPermission() }
            }
            else -> {
                status = getString(R.string.shizuku_ready)
                button = getString(R.string.shizuku_connected)
                action = { toast(getString(R.string.shizuku_ready)) }
            }
        }

        binding.tvShizukuStatus.text = getString(R.string.shizuku_status, status)
        binding.btnShizuku.text = button
        binding.btnShizuku.setOnClickListener { action() }

        if (ShizukuGate.ready()) lockDialog?.dismiss()
        else maybeShowLock(installed, ping, granted)
    }

    private fun maybeShowLock(installed: Boolean, ping: Boolean, granted: Boolean) {
        if (lockDialog?.isShowing == true) return
        if (installed && ping && granted) return
        lockDialog = AlertDialog.Builder(this)
            .setTitle(R.string.lock_title)
            .setMessage(R.string.lock_body)
            .setCancelable(false)
            .setPositiveButton(R.string.lock_grant) { _, _ -> ShizukuGate.requestPermission() }
            .setNeutralButton(R.string.lock_retry) { _, _ -> refreshShizuku() }
            .apply {
                if (!installed) {
                    setNegativeButton(R.string.lock_install) { _, _ -> Perms.openShizuku(this@MainActivity) }
                }
            }
            .show()
    }

    // ---------- Permissions rows ----------

    private fun setupRows() {
        with(binding) {
            rowAcc.tvLabel.text = getString(R.string.perm_accessibility)
            rowOverlay.tvLabel.text = getString(R.string.perm_overlay)
            rowNotif.tvLabel.text = getString(R.string.perm_notifications)
            rowDnd.tvLabel.text = getString(R.string.perm_dnd)

            rowAcc.root.setOnClickListener { Perms.openAccessibility(this@MainActivity) }
            rowOverlay.root.setOnClickListener { Perms.openOverlay(this@MainActivity) }
            rowNotif.root.setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Perms.openNotifications(this@MainActivity)
                }
            }
            rowDnd.root.setOnClickListener { Perms.openDnd(this@MainActivity) }
        }
    }

    private fun refreshPermissions() {
        binding.rowAcc.tvState.text = state(Perms.accessibilityEnabled(this, serviceName))
        binding.rowOverlay.tvState.text = state(Perms.canDrawOverlay(this))
        binding.rowNotif.tvState.text = state(Perms.notificationsEnabled(this))
        binding.rowDnd.tvState.text = state(Perms.dndGranted(this))
    }

    private fun state(on: Boolean) = if (on) "ON" else "TAP TO ENABLE"

    // ---------- Target game ----------

    private fun setupTarget() {
        binding.btnTarget.setOnClickListener { pickApp(getString(R.string.target_pick)) { info ->
            prefs.targetPackage = info.packageName
            refreshTarget()
        } }
    }

    private fun refreshTarget() {
        val pkg = prefs.targetPackage
        if (pkg.isNullOrEmpty()) {
            binding.btnTarget.text = getString(R.string.target_none)
            binding.tvTarget.text = ""
        } else {
            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (_: Throwable) { pkg }
            binding.btnTarget.text = label
            binding.tvTarget.text = pkg
        }
    }

    // ---------- Modes ----------

    private fun setupModes() {
        binding.btnModeLow.setOnClickListener { setMode(PerfMode.LOW) }
        binding.btnModeBalanced.setOnClickListener { setMode(PerfMode.BALANCED) }
        binding.btnModeMonster.setOnClickListener { setMode(PerfMode.MONSTER) }
    }

    private fun setMode(mode: PerfMode) {
        prefs.mode = mode.key
        refreshMode()
    }

    private fun refreshMode() {
        val cur = PerfMode.fromKey(prefs.mode)
        applyModeButton(binding.btnModeLow, PerfMode.LOW, cur == PerfMode.LOW)
        applyModeButton(binding.btnModeBalanced, PerfMode.BALANCED, cur == PerfMode.BALANCED)
        applyModeButton(binding.btnModeMonster, PerfMode.MONSTER, cur == PerfMode.MONSTER)
    }

    private fun applyModeButton(btn: android.widget.Button, mode: PerfMode, selected: Boolean) {
        val c = mode.color
        btn.background = if (selected)
            Drawables.rounded(color(R.color.surface_2), c, dp(14), 3)
        else
            Drawables.rounded(color(R.color.surface), color(R.color.stroke), dp(14), 1)
        btn.setTextColor(if (selected) c else color(R.color.text))
    }

    // ---------- Whitelist ----------

    private fun setupWhitelist() {
        binding.btnWhitelistAdd.setOnClickListener {
            val exclude = prefs.whitelist + (prefs.targetPackage ?: "")
            pickApp(getString(R.string.whitelist_add), exclude) { info ->
                prefs.addWhitelist(info.packageName)
                refreshWhitelist()
            }
        }
    }

    private fun refreshWhitelist() {
        binding.containerWhitelist.removeAllViews()
        val list = prefs.whitelist.toList().sorted()
        binding.tvWhitelistEmpty.visibility =
            if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        for (pkg in list) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val name = android.widget.TextView(this).apply {
                text = pkg
                setTextColor(color(R.color.text))
                textSize = 13f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val remove = android.widget.TextView(this).apply {
                text = "✕"
                setTextColor(color(R.color.text_sub))
                textSize = 16f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = true
                setOnClickListener {
                    prefs.removeWhitelist(pkg)
                    refreshWhitelist()
                }
            }
            row.addView(name)
            row.addView(remove)
            binding.containerWhitelist.addView(row)
        }
    }

    // ---------- App picker ----------

    private fun pickApp(title: String, exclude: Set<String> = emptySet(), cb: (AppInfo) -> Unit) {
        val apps = PackageUtils.launchableApps(this).filter { it.packageName !in exclude }
        if (apps.isEmpty()) {
            toast("No apps found")
            return
        }
        val labels = apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)) { _, which ->
                cb(apps[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Misc ----------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == C.REQ_NOTIF) refreshPermissions()
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
