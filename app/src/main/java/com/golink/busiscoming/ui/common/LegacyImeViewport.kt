package com.golink.busiscoming.ui.common

import android.graphics.Rect
import android.view.View
import com.golink.busiscoming.R

/**
 * `adjustNothing` 在 Android 10 及以下不會提供可靠的 IME Insets。
 * 舊系統改以視窗可見區底部判斷鍵盤覆蓋範圍。
 */
object LegacyImeViewport {
    fun visibleBottomInRoot(view: View): Int {
        val root = view.rootView
        if (root.height <= 0) return 0

        val visibleFrame = Rect()
        val rootLocation = IntArray(2)
        root.getWindowVisibleDisplayFrame(visibleFrame)
        root.getLocationOnScreen(rootLocation)
        val visibleFrameBottom = visibleFrame.bottom - rootLocation[1]
        val contentBottom = root.findViewById<View>(android.R.id.content)
            ?.takeIf { it.height > 0 }
            ?.let { content ->
                val contentLocation = IntArray(2)
                content.getLocationOnScreen(contentLocation)
                contentLocation[1] + content.height - rootLocation[1]
            }
            ?: root.height
        val topLevelContentBottom = root.findViewById<View>(R.id.topLevelFragmentContainer)
            ?.takeIf { it.height > 0 }
            ?.let { content ->
                val contentLocation = IntArray(2)
                content.getLocationOnScreen(contentLocation)
                contentLocation[1] + content.height - rootLocation[1]
            }
            ?: root.height
        return minOf(
            visibleFrameBottom,
            contentBottom,
            topLevelContentBottom
        ).coerceIn(0, root.height)
    }
}
