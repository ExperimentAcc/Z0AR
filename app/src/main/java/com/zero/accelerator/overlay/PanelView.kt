package com.zero.accelerator.overlay

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.util.Pal
import com.zero.accelerator.util.dp
import com.zero.accelerator.util.dpf
import com.zero.accelerator.util.rounded
import java.util.Locale

/** Everything the panel needs from its host. */
interface PanelActions {
    fun currentMode(): PerfMode
    fun onModeSelected(m: PerfMode)
    fun onRamClean()
    fun refreshHz(): Int
    fun onRefreshSelected(hz: Int)
    fun wifiActive(): Boolean
    fun mobileActive(): Boolean
    fun onNetwork(wifi: Boolean)
    fun tileActive(id: String): Boolean
    fun onTile(id: String)
    fun gameLabel(): String
    fun onClose()
}

/**
 * Left-side slide-in game panel: rings, mode switch, refresh rate, network, tiles.
 * A dim scrim covers the rest of the screen; tapping it closes the panel.
 */
class PanelView(
    ctx: Context,
    private val a: PanelActions,
    private val cardW: Int,
    hzOptions: List<Int>
) : FrameLayout(ctx) {

    private class Tile(val id: String, val label: String, val icon: IconView.Kind)

    private val tiles = listOf(
        Tile("dnd", "Block notifications", IconView.Kind.BELL),
        Tile("bright", "Lock brightness", IconView.Kind.SUN),
        Tile("awake", "Keep screen on", IconView.Kind.SCREEN),
        Tile("shot", "Screenshot", IconView.Kind.SHOT),
        Tile("ram", "RAM clean", IconView.Kind.CHIP),
        Tile("perf", "Fixed performance", IconView.Kind.BOLT),
        Tile("mute", "Mute", IconView.Kind.SPEAKER),
        Tile("settings", "Settings", IconView.Kind.GEAR)
    )

    private val scrim = View(ctx)
    private val scroller = ScrollView(ctx)
    private val col = LinearLayout(ctx)

    private val cpuGauge = RingGauge(ctx, "CPU", "GHz")
    private val gpuGauge = RingGauge(ctx, "GPU", "MHz")
    private val tvDuration = TextView(ctx)
    private val tvFps = TextView(ctx)
    private val tvBattery = TextView(ctx)

    private val modeCells = LinkedHashMap<PerfMode, TextView>()
    private val hzCells = LinkedHashMap<Int, TextView>()
    private lateinit var wifiCell: TextView
    private lateinit var mobileCell: TextView
    private val tileBoxes = HashMap<String, FrameLayout>()
    private val tileIcons = HashMap<String, IconView>()

    private val offscreen get() = -(cardW + context.dp(60)).toFloat()

    init {
        scrim.setBackgroundColor(0x88000000.toInt())
        scrim.setOnClickListener { a.onClose() }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ctx.dp(18), ctx.dp(14), ctx.dp(18), ctx.dp(18))
        scroller.addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroller.isVerticalScrollBarEnabled = false
        scroller.overScrollMode = View.OVER_SCROLL_NEVER
        scroller.background = rounded(Pal.PANEL, ctx.dpf(26f), 0x33FFFFFF, 1)
        scroller.clipToOutline = true
        scroller.outlineProvider = ViewOutlineProvider.BACKGROUND
        // Swallow touches so the scrim underneath is not triggered by taps on the card.
        scroller.isClickable = true

        val lp = LayoutParams(cardW, LayoutParams.MATCH_PARENT, Gravity.START)
        lp.setMargins(ctx.dp(10), ctx.dp(10), 0, ctx.dp(10))
        addView(scroller, lp)

        buildHeader()
        buildRings()
        buildStats()
        buildModes()
        buildRefresh(hzOptions)
        buildNetwork()
        buildTiles()

        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 28) {
                val left = insets.displayCutout?.safeInsetLeft ?: 0
                (scroller.layoutParams as LayoutParams).leftMargin = left + ctx.dp(10)
                scroller.requestLayout()
            }
            insets
        }
    }

    // ---------- building ----------

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun lin(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    private fun buildHeader() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bolt = IconView(context, IconView.Kind.BOLT).apply { tint = Pal.AMBER }
        row.addView(bolt, lin(context.dp(20), context.dp(20)).apply { rightMargin = context.dp(8) })

        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("ZERO ACCELERATOR", 12f, Pal.AMBER, true).apply { letterSpacing = 0.14f })
        titles.addView(text(a.gameLabel(), 11f, Pal.SUB).apply { maxLines = 1 })
        row.addView(titles, lin(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val close = text("\u2715", 18f, Pal.SUB).apply {
            setPadding(context.dp(12), context.dp(6), context.dp(4), context.dp(6))
            setOnClickListener { a.onClose() }
        }
        row.addView(close)
        col.addView(row, lin(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun buildRings() {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(cpuGauge, lin(0, context.dp(128), 1f))
        row.addView(gpuGauge, lin(0, context.dp(128), 1f))
        col.addView(row, lin(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(6)
        })
    }

    private fun stat(tv: TextView, label: String): LinearLayout {
        tv.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Pal.TEXT)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            gravity = Gravity.CENTER
            text = "--"
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(tv)
            addView(text(label, 11f, Pal.SUB).apply { gravity = Gravity.CENTER })
        }
    }

    private fun buildStats() {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(stat(tvDuration, "Game duration"), lin(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stat(tvFps, "FPS"), lin(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stat(tvBattery, "Battery"), lin(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row, lin(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(2)
        })
    }

    private fun section(title: String) {
        col.addView(text(title, 16f, Pal.TEXT, true), lin(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dp(18)
            bottomMargin = context.dp(8)
        })
    }

    private fun buildModes() {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(0xFF1D1D1D.toInt(), context.dpf(16f))
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        }
        for (m in PerfMode.values()) {
            val tv = TextView(context).apply {
                text = if (m == PerfMode.MONSTER) "MONSTER" else m.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                maxLines = 2
                setOnClickListener { a.onModeSelected(m) }
            }
            modeCells[m] = tv
            box.addView(tv, lin(0, context.dp(44), 1f))
        }
        col.addView(box, lin(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(14)
        })
    }

    private fun chip(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            gravity = Gravity.CENTER
            maxLines = 1
            setOnClickListener { onClick() }
        }

    private fun chipRow(cells: List<TextView>) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        cells.forEachIndexed { i, c ->
            row.addView(c, lin(0, context.dp(40), 1f).apply {
                if (i > 0) leftMargin = context.dp(8)
            })
        }
        col.addView(row, lin(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun buildRefresh(options: List<Int>) {
        section("Refresh rate")
        val cells = ArrayList<TextView>()
        val smart = chip("Smart") { a.onRefreshSelected(0) }
        hzCells[0] = smart
        cells.add(smart)
        for (hz in options) {
            val c = chip("$hz Hz") { a.onRefreshSelected(hz) }
            hzCells[hz] = c
            cells.add(c)
        }
        chipRow(cells)
    }

    private fun buildNetwork() {
        section("Network")
        wifiCell = chip("Wi-Fi") { a.onNetwork(true) }
        mobileCell = chip("Mobile data") { a.onNetwork(false) }
        chipRow(listOf(wifiCell, mobileCell))
    }

    private fun buildTiles() {
        section("Tools")
        var row: LinearLayout? = null
        tiles.forEachIndexed { i, t ->
            if (i % 4 == 0) {
                val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row = r
                col.addView(r, lin(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i > 0) topMargin = context.dp(12) })
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val box = FrameLayout(context)
            val icon = IconView(context, t.icon)
            box.addView(icon, LayoutParams(context.dp(26), context.dp(26), Gravity.CENTER))
            box.setOnClickListener {
                a.onTile(t.id)
                refreshStates()
            }
            tileBoxes[t.id] = box
            tileIcons[t.id] = icon
            cell.addView(box, lin(context.dp(56), context.dp(56)))
            cell.addView(text(t.label, 10.5f, 0xFFB8B8B8.toInt()).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }, lin(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(6)
            })
            row?.addView(cell, lin(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        // Pad the last row so cells keep their width.
        val rem = tiles.size % 4
        if (rem != 0) {
            for (k in 0 until 4 - rem) row?.addView(View(context), lin(0, 1, 1f))
        }
    }

    // ---------- state ----------

    private fun styleSelected(tv: TextView, selected: Boolean, italic: Boolean = false) {
        tv.background = if (selected) rounded(Pal.AMBER, context.dpf(12f)) else null
        tv.setTextColor(if (selected) Pal.ON_AMBER else Pal.SUB)
        tv.typeface = when {
            italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            selected -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
    }

    private fun styleChip(tv: TextView, selected: Boolean) {
        tv.background = rounded(
            if (selected) Pal.AMBER else 0xFF222222.toInt(), context.dpf(12f)
        )
        tv.setTextColor(if (selected) Pal.ON_AMBER else Pal.TEXT)
        tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun refreshStates() {
        val cur = a.currentMode()
        for ((m, tv) in modeCells) styleSelected(tv, m == cur, m == PerfMode.MONSTER)

        val hz = a.refreshHz()
        for ((k, tv) in hzCells) styleChip(tv, k == hz)

        styleChip(wifiCell, a.wifiActive())
        styleChip(mobileCell, a.mobileActive())

        for (t in tiles) {
            val on = a.tileActive(t.id)
            tileBoxes[t.id]?.background = rounded(
                if (on) Pal.AMBER else Pal.SURFACE2, context.dpf(18f)
            )
            tileIcons[t.id]?.let { icon ->
                icon.tint = if (on) Pal.ON_AMBER else Pal.TEXT
                icon.struck = on && (t.id == "dnd" || t.id == "mute")
            }
        }
    }

    fun onSample(s: Sample, elapsedMs: Long) {
        if (s.cpuKhz > 0) {
            cpuGauge.valueText = String.format(Locale.US, "%.2f", s.cpuKhz / 1_000_000.0)
            cpuGauge.fraction = if (s.cpuMaxKhz > 0) s.cpuKhz.toFloat() / s.cpuMaxKhz else 0f
        } else {
            cpuGauge.valueText = "--"
            cpuGauge.fraction = 0f
        }
        if (s.gpuMhz > 0) {
            gpuGauge.valueText = s.gpuMhz.toString()
            gpuGauge.fraction = if (s.gpuMaxMhz > 0) s.gpuMhz.toFloat() / s.gpuMaxMhz else 0f
        } else {
            gpuGauge.valueText = "--"
            gpuGauge.fraction = 0f
        }
        tvFps.text = if (s.fps >= 0) s.fps.toString() else "--"
        tvBattery.text = if (s.battery >= 0) "${s.battery}%" else "--"
        updateDuration(elapsedMs)
        refreshNetwork()
    }

    private fun refreshNetwork() {
        styleChip(wifiCell, a.wifiActive())
        styleChip(mobileCell, a.mobileActive())
    }

    fun updateDuration(ms: Long) {
        val s = ms / 1000
        tvDuration.text = if (s < 3600) {
            String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
        } else {
            String.format(Locale.US, "%.1f h", s / 3600.0)
        }
    }

    // ---------- animation ----------

    fun animateIn() {
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(200).start()
        scroller.translationX = offscreen
        scroller.animate().translationX(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun animateOut(end: () -> Unit) {
        scrim.animate().alpha(0f).setDuration(180).start()
        scroller.animate().translationX(offscreen).setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { end() }
            .start()
    }
}
