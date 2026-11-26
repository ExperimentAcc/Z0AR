package com.zero.accelerator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.zero.accelerator.R
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.shizuku.ShizukuGate
import com.zero.accelerator.util.DndUtil
import com.zero.accelerator.util.Drawables
import com.zero.accelerator.util.color
import kotlin.math.roundToInt

/**
 * Draws the floating launch banner, the persistent top-left edge control, and the
 * slide-down HUD sidebar. Everything lives in TYPE_APPLICATION_OVERLAY windows
 * that are added only while the bound game is in the foreground.
 */
class GameOverlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val dm = ctx.resources.displayMetrics

    private var banner: View? = null
    private var edge: View? = null
    private var sidebar: View? = null
    private var ticker: Runnable? = null

    private var lastMode: PerfMode = PerfMode.BALANCED
    private var lastTarget: String = ""
    private var lastExcluded: Set<String> = emptySet()
    private var sessionStart: Long = 0

    private fun dp(v: Int) = (v * dm.density).roundToInt()

    private fun lp(gravity: Int, w: Int, h: Int, x: Int = 0, y: Int = 0): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = gravity
        p.x = x
        p.y = y
        return p
    }

    fun showLaunchBanner(
        mode: PerfMode,
        deviceModel: String,
        target: String,
        excluded: Set<String>
    ) {
        lastMode = mode
        lastTarget = target
        lastExcluded = excluded
        sessionStart = System.currentTimeMillis()
        removeBanner()

        val v = LayoutInflater.from(ctx).inflate(R.layout.banner_pill, null)
        v.background = Drawables.rounded(
            ctx.color(R.color.surface_2), mode.color, dp(14)
        )
        v.findViewById<TextView>(R.id.banner_title).setTextColor(mode.color)
        v.findViewById<TextView>(R.id.banner_sub).text =
            ctx.getString(R.string.banner_assisted, deviceModel)
        v.findViewById<TextView>(R.id.banner_ticker).setTextColor(mode.color)

        val p = lp(
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            0,
            dp(24)
        )
        wm.addView(v, p)
        banner = v

        val steps = arrayOf(
            "Purging RAM",
            "Trimming caches",
            "Optimizing standby bucket",
            "Boosting clocks",
            "Locking focus"
        )
        var i = 0
        val tick = object : Runnable {
            override fun run() {
                v.findViewById<TextView>(R.id.banner_ticker).text = steps[i % steps.size]
                i++
                handler.postDelayed(this, 250)
            }
        }
        ticker = tick
        handler.post(tick)

        // Exit after 1.2s, leaving the subtle edge control behind.
        handler.postDelayed({ dismissBanner() }, 1200)
    }

    private fun dismissBanner() {
        val v = banner ?: return
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        v.animate()
            .translationY((-dp(140)).toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                wm.removeView(v)
                banner = null
                showEdge()
            }
    }

    private fun showEdge() {
        removeEdge()
        val v = LayoutInflater.from(ctx).inflate(R.layout.edge_control, null)
        v.background = Drawables.rounded(lastMode.color, lastMode.color, dp(8))
        v.alpha = 0.85f
        v.setOnClickListener { openSidebar() }
        val p = lp(Gravity.TOP or Gravity.START, dp(16), dp(64), 0, dp(48))
        wm.addView(v, p)
        edge = v
    }

    fun openSidebar() {
        if (sidebar != null) return
        val v = LayoutInflater.from(ctx).inflate(R.layout.sidebar_hud, null)
        v.background = Drawables.rounded(
            ctx.color(R.color.surface_2), lastMode.color, dp(14)
        )
        v.findViewById<TextView>(R.id.tvSideTitle).setTextColor(lastMode.color)
        v.findViewById<View>(R.id.tvClose).setOnClickListener { closeSidebar() }
        v.findViewById<View>(R.id.btnRam).setOnClickListener {
            ShizukuGate.optimize(lastMode, lastTarget, lastExcluded)
            flash(it)
        }
        v.findViewById<View>(R.id.btnDnd).setOnClickListener {
            if (DndUtil.granted(ctx)) DndUtil.toggle(ctx) else DndUtil.openSettings(ctx)
            flash(it)
        }

        val h = dp(300)
        val p = lp(
            Gravity.TOP or Gravity.FILL_HORIZONTAL,
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
            0,
            0
        )
        wm.addView(v, p)
        sidebar = v
        v.translationY = (-h).toFloat()
        v.animate().translationY(0f).setDuration(300)
        Telemetry.start(ctx, v, sessionStart, lastMode)
    }

    fun closeSidebar() {
        val v = sidebar ?: return
        sidebar = null
        Telemetry.stop()
        v.animate()
            .translationY((-v.height).toFloat())
            .setDuration(250)
            .withEndAction {
                try { wm.removeView(v) } catch (_: Throwable) {}
            }
    }

    private fun flash(view: View) {
        view.animate().alpha(0.4f).setDuration(90).withEndAction {
            view.animate().alpha(1f).duration = 160
        }
    }

    private fun removeBanner() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        banner?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        banner = null
    }

    private fun removeEdge() {
        edge?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        edge = null
    }

    /** Tear down every window — used when the game leaves the foreground. */
    fun teardown() {
        removeBanner()
        removeEdge()
        sidebar?.let {
            Telemetry.stop()
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        sidebar = null
    }
}
