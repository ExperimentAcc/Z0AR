package com.zero.accelerator.util

import android.content.Context
import androidx.core.content.ContextCompat

/** Resolve a color resource to its packed ARGB value. */
fun Context.color(res: Int): Int = ContextCompat.getColor(this, res)
