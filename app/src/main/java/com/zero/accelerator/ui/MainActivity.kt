package com.zero.accelerator.ui

import android.Manifest
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.zero.accelerator.BuildConfig
import com.zero.accelerator.GameAccessibilityService
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.overlay.IconView
import com.zero.accelerator.prefs.AppPrefs
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.AppInfo
import com.zero.accelerator.util.DndUtil
import com.zero.accelerator.util.PackageUtils
import com.zero.accelerator.util.Pal
import com.zero.accelerator.util.Perms
import com.zero.accelerator.util.dp
import com.zero.accelerator.util.dpf
import com.zero.accelerator.util.rounded
import com.zero.accelerator.ui.Ui.MATCH
import com.zero.accelerator.ui.Ui.WRAP

/**
 * Setup dashboard. Nothing here blocks the user: Shizuku is optional, and every
 * missing requirement is shown as a tappable row instead of a modal dialog.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var content: LinearLayout

    // `this` inside apply{} blocks is the view, so keep a handle on the activity.
    private val act: Context get() = this

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { render() }

    private val statusListener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        ShizukuGate.init(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        ShizukuGate.addStatusListener(statusListener)
    }

    override fun onStop() {
        ShizukuGate.removeStatusListener(statusListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------- rendering ----------

    private fun render() {
        content.removeAllViews()
        header()
        hero()
        setupCard()
        targetCard()
        modeCard()
        overlayCard()
        whitelistCard()
        previewCard()
        footer()
    }

    private fun card(title: String? = null): LinearLayout {
        val c = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Pal.SURFACE, dpf(18f), Pal.STROKE, 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        if (title != null) {
            c.addView(
                Ui.text(act, title.uppercase(), 11f, Pal.SUB, true).apply { letterSpacing = 0.14f },
                Ui.lp(WRAP, WRAP, b = dp(8))
            )
        }
        content.addView(c, Ui.lp(MATCH, WRAP, b = dp(12)))
        return c
    }

    private fun header() {
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = FrameLayoutBox()
        row.addView(logo, Ui.lp(dp(46), dp(46), r = dp(12)))
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(act, "ZERO ACCELERATOR", 20f, Pal.TEXT, true).apply { letterSpacing = 0.08f })
        col.addView(Ui.text(act, "Game boost  |  Shizuku powered", 12f, Pal.SUB))
        row.addView(col)
        content.addView(row, Ui.lp(MATCH, WRAP, b = dp(16)))
    }

    private fun FrameLayoutBox(): View {
        val box = android.widget.FrameLayout(act)
        box.background = rounded(Pal.AMBER, dpf(14f))
        val icon = IconView(act, IconView.Kind.BOLT).apply { tint = Pal.ON_AMBER }
        box.addView(icon, android.widget.FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        return box
    }

    private fun hero() {
        val acc = Perms.accessibilityEnabled(this)
        val target = prefs.targetPackage
        val armed = acc && target != null
        val c = card()
        c.addView(
            Ui.text(act, if (armed) "ARMED" else "SETUP NEEDED", 26f,
                if (armed) Pal.GREEN else Pal.AMBER, true).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            }
        )
        val msg = when {
            !acc -> "Turn on the accessibility service below. It is what detects your game."
            target == null -> "Choose the game you want to boost."
            else -> "Open ${PackageUtils.label(this, target)}. The boot splash and side panel appear automatically."
        }
        c.addView(Ui.text(act, msg, 13f, Pal.SUB), Ui.lp(MATCH, WRAP, t = dp(4)))
        if (armed && !ShizukuGate.ready()) {
            c.addView(
                Ui.text(act, "Shizuku is offline: splash and panel work, but cleaning and system toggles are disabled.",
                    12f, Pal.AMBER),
                Ui.lp(MATCH, WRAP, t = dp(8))
            )
        }
    }

    private fun row(
        parent: LinearLayout, title: String, desc: String,
        pill: String, pillColor: Int, onClick: (() -> Unit)?
    ) {
        val r = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(act, title, 15f, Pal.TEXT, true))
        col.addView(Ui.text(act, desc, 12f, Pal.SUB))
        r.addView(col, Ui.lp(0, WRAP, weight = 1f))
        r.addView(Ui.pill(act, pill, pillColor))
        parent.addView(r, Ui.lp(MATCH, WRAP))
    }

    private fun setupCard() {
        val c = card("Setup")

        val acc = Perms.accessibilityEnabled(this)
        row(c, "Accessibility service", "Required. Detects when your game opens.",
            if (acc) "ON" else "ENABLE", if (acc) Pal.GREEN else Pal.AMBER) {
            Perms.openAccessibility(this)
        }
        if (!acc) {
            c.addView(
                Ui.text(act,
                    "Toggle greyed out? Android blocks sideloaded apps. Open App info, tap the three dots, " +
                        "choose \"Allow restricted settings\", then enable the service again.",
                    12f, Pal.AMBER),
                Ui.lp(MATCH, WRAP, b = dp(6))
            )
            c.addView(Ui.pill(act, "OPEN APP INFO", Pal.AMBER).apply {
                setOnClickListener { Perms.openAppInfo(this@MainActivity) }
            }, Ui.lp(WRAP, WRAP, b = dp(6)))
        }

        val installed = Perms.shizukuInstalled(this)
        val ping = ShizukuGate.ping()
        val granted = ShizukuGate.granted()
        when {
            !installed -> row(c, "Shizuku", "Recommended. Needed for cleaning and system toggles.",
                "GET", Pal.AMBER) { Perms.openShizuku(this) }
            !ping -> row(c, "Shizuku", "Installed but not running. Start it with ADB or root.",
                "START", Pal.AMBER) { Perms.openShizuku(this) }
            !granted -> row(c, "Shizuku", "Running. Allow ZERO ACCELERATOR to use it.",
                "ALLOW", Pal.AMBER) {
                if (!ShizukuGate.requestPermission()) {
                    toast("Open Shizuku and grant ZERO ACCELERATOR manually")
                    Perms.openShizuku(this)
                }
            }
            else -> row(c, "Shizuku", "Connected.", "READY", Pal.GREEN, null)
        }

        val notif = Perms.notificationsEnabled(this)
        row(c, "Notifications", "Used for the MONSTER session notice.",
            if (notif) "ON" else "ALLOW", if (notif) Pal.GREEN else Pal.SUB) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Perms.openNotifications(this)
        }

        val dnd = DndUtil.granted(this)
        row(c, "Do Not Disturb access", "Lets the panel block notifications in game.",
            if (dnd) "ON" else "ALLOW", if (dnd) Pal.GREEN else Pal.SUB) { Perms.openDnd(this) }

        val bat = Perms.batteryUnrestricted(this)
        row(c, "Battery unrestricted", "Stops the system from killing the service.",
            if (bat) "ON" else "SET", if (bat) Pal.GREEN else Pal.SUB) { Perms.openBattery(this) }
    }

    private fun targetCard() {
        val c = card("Target game")
        val pkg = prefs.targetPackage
        val r = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (pkg != null) {
            val icon = try { packageManager.getApplicationIcon(pkg) } catch (_: Throwable) { null }
            if (icon != null) {
                r.addView(ImageView(act).apply { setImageDrawable(icon) }, Ui.lp(dp(42), dp(42), r = dp(12)))
            }
        }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(act, if (pkg == null) "No game selected" else PackageUtils.label(this, pkg),
            16f, Pal.TEXT, true))
        col.addView(Ui.text(act, pkg ?: "Tap CHOOSE to pick one", 11f, Pal.SUB))
        r.addView(col, Ui.lp(0, WRAP, weight = 1f))
        r.addView(Ui.pill(act, "CHOOSE", Pal.AMBER).apply {
            setOnClickListener {
                pickApp("Choose target game", emptySet()) { info ->
                    prefs.targetPackage = info.packageName
                    render()
                }
            }
        })
        c.addView(r)
        if (pkg != null) {
            c.addView(Ui.pill(act, "LAUNCH GAME", Pal.GREEN).apply {
                setOnClickListener {
                    if (!Perms.launchGame(this@MainActivity, pkg)) toast("Cannot launch this app")
                }
            }, Ui.lp(WRAP, WRAP, t = dp(12)))
        }
    }

    private fun modeCard() {
        val c = card("Performance mode")
        val cur = PerfMode.fromKey(prefs.mode)
        for (m in PerfMode.values()) {
            val sel = m == cur
            val r = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(if (sel) 0xFF20190A.toInt() else Pal.SURFACE2, dpf(14f),
                    if (sel) Pal.AMBER else Pal.SURFACE2, if (sel) 2 else 1)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                isClickable = true
                setOnClickListener {
                    prefs.mode = m.key
                    render()
                }
            }
            r.addView(Ui.text(act, m.label.uppercase(), 14f, if (sel) Pal.AMBER else Pal.TEXT, true).apply {
                if (m == PerfMode.MONSTER) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            })
            r.addView(Ui.text(act, m.desc, 12f, Pal.SUB))
            c.addView(r, Ui.lp(MATCH, WRAP, b = dp(8)))
        }
    }

    private fun switchRow(parent: LinearLayout, title: String, desc: String, on: Boolean, change: (Boolean) -> Unit) {
        val r = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.text(act, title, 15f, Pal.TEXT, true))
        col.addView(Ui.text(act, desc, 12f, Pal.SUB))
        r.addView(col, Ui.lp(0, WRAP, weight = 1f))
        val sw = SwitchCompat(act).apply {
            isChecked = on
            setOnCheckedChangeListener { _, v -> change(v) }
        }
        r.addView(sw)
        parent.addView(r, Ui.lp(MATCH, WRAP))
    }

    private fun overlayCard() {
        val c = card("In-game overlay")
        switchRow(c, "Boot splash", "Branded loading screen while the game starts.", prefs.splash) {
            prefs.splash = it
        }
        switchRow(c, "Keep screen on", "Prevents sleep while the game is open.", prefs.keepAwake) {
            prefs.keepAwake = it
        }
        c.addView(Ui.text(act, "Side handle height", 15f, Pal.TEXT, true), Ui.lp(WRAP, WRAP, t = dp(10)))
        c.addView(Ui.text(act, "Where the amber pill sits on the left edge. Tap or swipe it to open the panel.",
            12f, Pal.SUB))
        val bar = SeekBar(act).apply {
            max = 320
            progress = prefs.handleY.coerceIn(0, 320)
            progressTintList = android.content.res.ColorStateList.valueOf(Pal.AMBER)
            thumbTintList = android.content.res.ColorStateList.valueOf(Pal.AMBER)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    prefs.handleY = s?.progress ?: 64
                }
            })
        }
        c.addView(bar, Ui.lp(MATCH, WRAP, t = dp(4)))
    }

    private fun whitelistCard() {
        val c = card("Never stop (whitelist)")
        c.addView(Ui.text(act, "Keyboard and launcher are always protected.", 12f, Pal.SUB), Ui.lp(WRAP, WRAP, b = dp(6)))
        val list = prefs.whitelist.toList().sorted()
        if (list.isEmpty()) {
            c.addView(Ui.text(act, "Nothing whitelisted", 13f, Pal.SUB), Ui.lp(WRAP, WRAP, b = dp(6)))
        }
        for (pkg in list) {
            val r = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            r.addView(Ui.text(act, PackageUtils.label(this, pkg) + "\n" + pkg, 12f, Pal.TEXT),
                Ui.lp(0, WRAP, weight = 1f))
            r.addView(Ui.text(act, "\u2715", 17f, Pal.SUB).apply {
                setPadding(dp(14), dp(8), dp(6), dp(8))
                setOnClickListener {
                    prefs.removeWhitelist(pkg)
                    render()
                }
            })
            c.addView(r, Ui.lp(MATCH, WRAP))
        }
        c.addView(Ui.pill(act, "ADD APP", Pal.AMBER).apply {
            setOnClickListener {
                pickApp("Add to whitelist", prefs.whitelist + (prefs.targetPackage ?: "")) { info ->
                    prefs.addWhitelist(info.packageName)
                    render()
                }
            }
        }, Ui.lp(WRAP, WRAP, t = dp(8)))
    }

    private fun previewCard() {
        val c = card("Test")
        c.addView(Ui.text(act,
            "Preview the splash and side panel without opening the game. Cleaning is skipped in preview.",
            12f, Pal.SUB), Ui.lp(MATCH, WRAP, b = dp(10)))
        val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(Ui.pill(act, "PREVIEW", Pal.AMBER).apply {
            setOnClickListener {
                val s = GameAccessibilityService.instance
                if (s == null) toast("Enable the accessibility service first") else s.preview()
            }
        }, Ui.lp(WRAP, WRAP, r = dp(10)))
        r.addView(Ui.pill(act, "STOP", Pal.SUB).apply {
            setOnClickListener { GameAccessibilityService.instance?.stopPreview() }
        })
        c.addView(r)
    }

    private fun footer() {
        content.addView(
            Ui.text(act, "v${BuildConfig.VERSION_NAME}", 11f, Pal.SUB).apply { gravity = Gravity.CENTER },
            Ui.lp(MATCH, WRAP, t = dp(4))
        )
    }

    // ---------- app picker with search ----------

    private class AppAdapter(private val ctx: Context, private val all: List<AppInfo>) : BaseAdapter() {
        private var shown: List<AppInfo> = all

        fun filter(q: String) {
            val s = q.trim().lowercase()
            shown = if (s.isEmpty()) all else all.filter {
                it.label.lowercase().contains(s) || it.packageName.lowercase().contains(s)
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): AppInfo = shown[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val info = shown[position]
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
            }
            val icon = try { ctx.packageManager.getApplicationIcon(info.packageName) } catch (_: Throwable) { null }
            if (icon != null) {
                row.addView(ImageView(ctx).apply { setImageDrawable(icon) }, Ui.lp(ctx.dp(36), ctx.dp(36), r = ctx.dp(12)))
            }
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Ui.text(ctx, info.label, 15f, Pal.TEXT, true))
            col.addView(Ui.text(ctx, info.packageName, 11f, Pal.SUB))
            row.addView(col)
            return row
        }
    }

    private fun pickApp(title: String, exclude: Set<String>, cb: (AppInfo) -> Unit) {
        val apps = PackageUtils.launchableApps(this).filter { it.packageName !in exclude }
        if (apps.isEmpty()) {
            toast("No apps found")
            return
        }
        val appAdapter = AppAdapter(this, apps)
        val list = ListView(this)
        list.adapter = appAdapter
        val search = EditText(this).apply {
            hint = "Search"
            setHintTextColor(Pal.SUB)
            setTextColor(Pal.TEXT)
            isSingleLine = true
            setPadding(dp(18), dp(10), dp(18), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    appAdapter.filter(s?.toString() ?: "")
                }
            })
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(search, Ui.lp(MATCH, WRAP))
        box.addView(list, Ui.lp(MATCH, dp(420)))

        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, pos, _ ->
            cb(appAdapter.getItem(pos))
            dlg.dismiss()
        }
        dlg.show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
