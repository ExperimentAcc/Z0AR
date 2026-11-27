package com.zero.accelerator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.zero.accelerator.util.Pal
import kotlin.math.cos
import kotlin.math.sin

/** Tiny canvas-drawn icon set (24 unit grid) so the APK needs no icon assets. */
class IconView(ctx: Context, private val kind: Kind) : View(ctx) {

    enum class Kind { BELL, SUN, SCREEN, SHOT, CHIP, BOLT, SPEAKER, GEAR }

    var tint: Int = Pal.TEXT
        set(v) {
            field = v
            invalidate()
        }

    /** Draws a diagonal slash (muted / blocked state). */
    var struck: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        stroke.color = tint
        fill.color = tint
        c.save()
        c.scale(width / 24f, height / 24f)
        when (kind) {
            Kind.BELL -> {
                path.reset()
                path.moveTo(5.5f, 17f)
                path.lineTo(18.5f, 17f)
                path.lineTo(17f, 15.5f)
                path.lineTo(17f, 10.5f)
                path.cubicTo(17f, 7.6f, 14.8f, 5.6f, 12f, 5.6f)
                path.cubicTo(9.2f, 5.6f, 7f, 7.6f, 7f, 10.5f)
                path.lineTo(7f, 15.5f)
                path.close()
                c.drawPath(path, stroke)
                c.drawLine(12f, 3.4f, 12f, 5.6f, stroke)
                rect.set(10f, 17.8f, 14f, 21.2f)
                c.drawArc(rect, 0f, 180f, false, stroke)
            }
            Kind.SUN -> {
                c.drawCircle(12f, 12f, 3.8f, stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val cs = cos(a).toFloat()
                    val sn = sin(a).toFloat()
                    c.drawLine(12f + cs * 6.8f, 12f + sn * 6.8f, 12f + cs * 9.2f, 12f + sn * 9.2f, stroke)
                }
            }
            Kind.SCREEN -> {
                rect.set(6.5f, 3f, 17.5f, 21f)
                c.drawRoundRect(rect, 2.6f, 2.6f, stroke)
                c.drawCircle(12f, 11f, 2.2f, fill)
                c.drawLine(10f, 17.5f, 14f, 17.5f, stroke)
            }
            Kind.SHOT -> {
                path.reset()
                path.moveTo(4f, 9f); path.lineTo(4f, 4f); path.lineTo(9f, 4f)
                path.moveTo(15f, 4f); path.lineTo(20f, 4f); path.lineTo(20f, 9f)
                path.moveTo(20f, 15f); path.lineTo(20f, 20f); path.lineTo(15f, 20f)
                path.moveTo(9f, 20f); path.lineTo(4f, 20f); path.lineTo(4f, 15f)
                c.drawPath(path, stroke)
                c.drawCircle(12f, 12f, 3.2f, stroke)
            }
            Kind.CHIP -> {
                rect.set(7f, 7f, 17f, 17f)
                c.drawRoundRect(rect, 1.8f, 1.8f, stroke)
                rect.set(10f, 10f, 14f, 14f)
                c.drawRect(rect, fill)
                for (p in floatArrayOf(9.5f, 12f, 14.5f)) {
                    c.drawLine(p, 4f, p, 7f, stroke)
                    c.drawLine(p, 17f, p, 20f, stroke)
                    c.drawLine(4f, p, 7f, p, stroke)
                    c.drawLine(17f, p, 20f, p, stroke)
                }
            }
            Kind.BOLT -> {
                path.reset()
                path.moveTo(13.5f, 2.5f)
                path.lineTo(5f, 13.5f)
                path.lineTo(11f, 13.5f)
                path.lineTo(9.5f, 21.5f)
                path.lineTo(19f, 9.5f)
                path.lineTo(13f, 9.5f)
                path.close()
                c.drawPath(path, fill)
            }
            Kind.SPEAKER -> {
                path.reset()
                path.moveTo(4f, 9.5f)
                path.lineTo(8f, 9.5f)
                path.lineTo(12.5f, 5.5f)
                path.lineTo(12.5f, 18.5f)
                path.lineTo(8f, 14.5f)
                path.lineTo(4f, 14.5f)
                path.close()
                c.drawPath(path, stroke)
                rect.set(9.5f, 8f, 17f, 16f)
                c.drawArc(rect, -55f, 110f, false, stroke)
                rect.set(8f, 5f, 20f, 19f)
                c.drawArc(rect, -50f, 100f, false, stroke)
            }
            Kind.GEAR -> {
                c.drawCircle(12f, 12f, 3f, stroke)
                c.drawCircle(12f, 12f, 6.2f, stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val cs = cos(a).toFloat()
                    val sn = sin(a).toFloat()
                    c.drawLine(12f + cs * 6.2f, 12f + sn * 6.2f, 12f + cs * 9.3f, 12f + sn * 9.3f, stroke)
                }
            }
        }
        if (struck) c.drawLine(4f, 4f, 20f, 20f, stroke)
        c.restore()
    }
}
