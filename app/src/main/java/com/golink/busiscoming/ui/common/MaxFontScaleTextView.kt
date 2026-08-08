package com.golink.busiscoming.ui.common

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import kotlin.math.min
import kotlin.math.roundToInt

class MaxFontScaleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    init {
        val density = resources.displayMetrics.density
        val cappedScale = min(resources.configuration.fontScale, MAX_FONT_SCALE)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this,
            (MIN_TEXT_SIZE_SP * density * cappedScale).roundToInt(),
            (MAX_TEXT_SIZE_SP * density * cappedScale).roundToInt(),
            (TEXT_SIZE_STEP_SP * density * cappedScale).roundToInt().coerceAtLeast(1),
            TypedValue.COMPLEX_UNIT_PX
        )
    }

    private companion object {
        const val MAX_FONT_SCALE = 1.3f
        const val MIN_TEXT_SIZE_SP = 4f
        const val MAX_TEXT_SIZE_SP = 7f
        const val TEXT_SIZE_STEP_SP = 0.5f
    }
}
