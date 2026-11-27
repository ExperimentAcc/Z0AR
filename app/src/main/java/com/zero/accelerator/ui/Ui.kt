package com.zero.accelerator.ui

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.zero.accelerator.util.dpf
import com.zero.accelerator.util.dp
import com.zero.accelerator.util.rounded

/** Small helpers for building the dashboard in code (no XML, no ViewBinding). */
object Ui {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun lp(
        w: Int, h: Int,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0,
        weight: Float = 0f
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight).apply { setMargins(l, t, r, b) }

    fun pill(ctx: Context, s: String, color: Int): TextView =
        text(ctx, s, 11f, 0xFF111111.toInt(), true).apply {
            setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
            background = rounded(color, ctx.dpf(20f))
        }
}
