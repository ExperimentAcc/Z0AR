package com.zero.accelerator.util

import android.content.Context
import android.graphics.drawable.GradientDrawable
import kotlin.math.roundToInt

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/** Rounded rectangle with optional stroke. */
fun rounded(fill: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }
