package com.golink.busiscoming.ui.main

enum class UpdatePromptLayoutMode {
    HORIZONTAL,
    VERTICAL
}

object UpdatePromptLayoutPolicy {
    private const val MIN_HORIZONTAL_WIDTH_DP = 360
    private const val VERTICAL_FONT_SCALE = 2.0f

    fun resolve(
        screenWidthDp: Int,
        fontScale: Float
    ): UpdatePromptLayoutMode {
        return if (
            screenWidthDp < MIN_HORIZONTAL_WIDTH_DP ||
            fontScale >= VERTICAL_FONT_SCALE
        ) {
            UpdatePromptLayoutMode.VERTICAL
        } else {
            UpdatePromptLayoutMode.HORIZONTAL
        }
    }
}
