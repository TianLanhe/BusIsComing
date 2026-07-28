package com.golink.busiscoming.ui.main

import android.view.View
import com.golink.busiscoming.R

object RoutePinGestureHitTest {
    fun isExcluded(card: View, rawX: Float, rawY: Float): Boolean {
        return isInside(card.findViewById(R.id.busEtaTextColumn), rawX, rawY) ||
            isInside(card.findViewById(R.id.busMonitorButton), rawX, rawY)
    }

    private fun isInside(view: View?, rawX: Float, rawY: Float): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] &&
            rawX <= location[0] + view.width &&
            rawY >= location[1] &&
            rawY <= location[1] + view.height
    }
}
