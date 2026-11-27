package com.zero.accelerator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import com.zero.accelerator.util.Pal
import kotlin.math.min

/** Amber ring gauge: label on top, big value, unit below. */
class RingGauge(ctx: Context, private val label: String, private val unit: String) : View(ctx) {

    var valueText: String = "--"
        set(v) {
            field = v
            invalidate()
        }

    var fraction: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x24FFFFFF
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x33FFC72C
    }
    private val tLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.AMBER
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.TEXT
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
    }
    private val tUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.SUB
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(Pal.ORANGE, Pal.AMBER),
            floatArrayOf(0f, 0.75f)
        )
        val m = Matrix()
        m.postRotate(135f, w / 2f, h / 2f)
        shader.setLocalMatrix(m)
        arc.shader = shader
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val sw = s * 0.075f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        glow.strokeWidth = sw * 2.1f
        tLabel.textSize = s * 0.115f
        tValue.textSize = s * 0.24f
        tUnit.textSize = s * 0.115f

        val cx = width / 2f
        val cy = height / 2f
        val m = sw * 1.4f
        rect.set(cx - s / 2f + m, cy - s / 2f + m, cx + s / 2f - m, cy + s / 2f - m)

        c.drawArc(rect, 135f, 270f, false, track)
        val sweep = 270f * fraction
        if (sweep > 1f) {
            c.drawArc(rect, 135f, sweep, false, glow)
            c.drawArc(rect, 135f, sweep, false, arc)
        }
        c.drawText(label, cx, cy - s * 0.17f, tLabel)
        c.drawText(valueText, cx, cy + s * 0.09f, tValue)
        c.drawText(unit, cx, cy + s * 0.25f, tUnit)
    }
}
