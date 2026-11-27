package com.zero.accelerator.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.zero.accelerator.model.PerfMode
import com.zero.accelerator.util.Pal
import com.zero.accelerator.util.dp
import com.zero.accelerator.util.dpf
import com.zero.accelerator.util.rounded
import kotlin.math.min

/** Full-screen, non-touchable boot splash shown while the game starts. */
class SplashView(
    ctx: Context,
    mode: PerfMode,
    gameName: String,
    icon: Drawable?,
    device: String
) : FrameLayout(ctx) {

    private val step = TextView(ctx)
    private val bar = BarView(ctx)

    init {
        setBackgroundColor(0xF2070707.toInt())

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        col.addView(SpinnerView(ctx), LinearLayout.LayoutParams(ctx.dp(104), ctx.dp(104)))

        col.addView(label(ctx, "ZERO ACCELERATOR", 20f, Pal.TEXT, true, 0.16f).apply {
            setPadding(0, ctx.dp(14), 0, 0)
        })
        col.addView(label(ctx, "ASSISTED BY ${device.uppercase()}", 11f, Pal.SUB, false, 0.2f).apply {
            setPadding(0, ctx.dp(4), 0, 0)
        })

        val gameRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(14), ctx.dp(8))
            background = rounded(0x22FFFFFF, ctx.dpf(22f))
        }
        if (icon != null) {
            val iv = ImageView(ctx).apply { setImageDrawable(icon) }
            gameRow.addView(iv, LinearLayout.LayoutParams(ctx.dp(26), ctx.dp(26)).apply {
                rightMargin = ctx.dp(10)
            })
        }
        gameRow.addView(label(ctx, gameName, 14f, Pal.TEXT, true, 0f))
        gameRow.addView(label(ctx, mode.short, 12f, mode.color, true, 0.1f).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            setPadding(ctx.dp(12), 0, 0, 0)
        })
        col.addView(gameRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(22) })

        step.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Pal.AMBER)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            text = "INITIALIZING"
            gravity = Gravity.CENTER
        }
        col.addView(step, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(22) })

        col.addView(bar, LinearLayout.LayoutParams(ctx.dp(190), ctx.dp(3)).apply {
            topMargin = ctx.dp(10)
        })

        addView(col, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun label(ctx: Context, s: String, sp: Float, color: Int, bold: Boolean, spacing: Float): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            letterSpacing = spacing
            gravity = Gravity.CENTER
        }

    fun setStep(s: String) {
        step.text = s
    }

    fun setProgress(f: Float) {
        bar.progress = f
    }
}

private class SpinnerView(ctx: Context) : View(ctx) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Pal.AMBER
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22FFFFFF
    }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.AMBER }
    private val boltPath = Path().apply {
        moveTo(13.5f, 2.5f)
        lineTo(5f, 13.5f)
        lineTo(11f, 13.5f)
        lineTo(9.5f, 21.5f)
        lineTo(19f, 9.5f)
        lineTo(13f, 9.5f)
        close()
    }
    private val rect = RectF()
    private var angle = 0f

    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val sw = s * 0.055f
        ring.strokeWidth = sw
        track.strokeWidth = sw
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - s / 2f + sw, cy - s / 2f + sw, cx + s / 2f - sw, cy + s / 2f - sw)
        c.drawArc(rect, 0f, 360f, false, track)
        c.drawArc(rect, angle, 100f, false, ring)
        c.drawArc(rect, angle + 180f, 40f, false, ring)

        val k = s * 0.42f / 24f
        c.save()
        c.translate(cx - 12f * k, cy - 12f * k)
        c.scale(k, k)
        c.drawPath(boltPath, bolt)
        c.restore()
    }
}

private class BarView(ctx: Context) : View(ctx) {

    var progress: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.AMBER }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, r, r, track)
        rect.set(0f, 0f, width * progress, height.toFloat())
        c.drawRoundRect(rect, r, r, fill)
    }
}
