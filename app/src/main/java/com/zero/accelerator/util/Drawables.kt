package com.zero.accelerator.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object Drawables {
    /** Build a rounded rectangle with a solid fill and a thin stroke. */
    fun rounded(
        @androidx.annotation.ColorInt fill: Int,
        @androidx.annotation.ColorInt stroke: Int,
        radiusPx: Int,
        strokePx: Int = 2
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(strokePx, stroke)
        cornerRadius = radiusPx.toFloat()
    }

    fun withAlpha(@androidx.annotation.ColorInt color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
