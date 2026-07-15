package com.golink.busiscoming.ui.common

import android.graphics.text.LineBreaker
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.TextView

fun TextView.applyStableShortTextLayout(
    textGravity: Int = Gravity.START,
    alignment: Int = View.TEXT_ALIGNMENT_GRAVITY
): TextView {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        justificationMode = LineBreaker.JUSTIFICATION_MODE_NONE
    }
    letterSpacing = 0f
    gravity = textGravity
    textAlignment = alignment
    return this
}
