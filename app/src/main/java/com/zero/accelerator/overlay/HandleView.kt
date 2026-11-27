package com.zero.accelerator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.zero.accelerator.util.Pal
import kotlin.math.abs

/** Slim amber pill on the left edge. Tap it or drag it to open the panel. */
class HandleView(ctx: Context, private val onOpen: () -> Unit) : View(ctx) {

    var onTouched: (() -> Unit)? = null

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.AMBER }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val rf = RectF()
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val pw = 5f * d
        val ph = 46f * d
        val left = 10f * d
        val top = (height - ph) / 2f
        rf.set(left - d, top - d, left + pw + d, top + ph + d)
        c.drawRoundRect(rf, pw, pw, edge)
        rf.set(left, top, left + pw, top + ph)
        c.drawRoundRect(rf, pw / 2f, pw / 2f, pill)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                fired = false
                onTouched?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fired && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop)) {
                    fired = true
                    onOpen()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!fired) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onOpen()
        return true
    }
}
