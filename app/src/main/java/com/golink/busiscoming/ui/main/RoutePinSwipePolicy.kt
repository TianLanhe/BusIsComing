package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.PinLevel
import kotlin.math.abs

enum class RoutePinSwipeAction {
    PIN_TEMPORARY,
    PIN_PERSISTENT,
    CANCEL,
    REBOUND,
    UNAVAILABLE
}

object RoutePinSwipePolicy {
    const val SWIPE_THRESHOLD = 0.4f
    const val hasFlingShortcut = false

    fun action(
        pinLevel: PinLevel,
        eligible: Boolean,
        deltaX: Float,
        width: Float,
        velocityX: Float = 0f
    ): RoutePinSwipeAction {
        @Suppress("UNUSED_VARIABLE")
        val ignoredVelocity = velocityX
        if (!eligible) return RoutePinSwipeAction.UNAVAILABLE
        if (width <= 0f || abs(deltaX) < width * SWIPE_THRESHOLD) {
            return RoutePinSwipeAction.REBOUND
        }
        return if (deltaX > 0f) {
            when (pinLevel) {
                PinLevel.UNPINNED -> RoutePinSwipeAction.PIN_TEMPORARY
                PinLevel.TEMPORARY -> RoutePinSwipeAction.PIN_PERSISTENT
                PinLevel.PERSISTENT -> RoutePinSwipeAction.REBOUND
            }
        } else {
            when (pinLevel) {
                PinLevel.UNPINNED -> RoutePinSwipeAction.REBOUND
                PinLevel.TEMPORARY,
                PinLevel.PERSISTENT -> RoutePinSwipeAction.CANCEL
            }
        }
    }

    fun isMutatingDirection(pinLevel: PinLevel, eligible: Boolean, deltaX: Float): Boolean {
        if (!eligible || deltaX == 0f) return false
        return if (deltaX > 0f) {
            pinLevel != PinLevel.PERSISTENT
        } else {
            pinLevel != PinLevel.UNPINNED
        }
    }
}

class RoutePinSwipeThresholdTracker {
    private var hapticSent = false

    fun shouldHaptic(
        pinLevel: PinLevel,
        eligible: Boolean,
        deltaX: Float,
        width: Float
    ): Boolean {
        if (hapticSent || width <= 0f) return false
        val crossed = abs(deltaX) >= width * RoutePinSwipePolicy.SWIPE_THRESHOLD
        if (!crossed || !RoutePinSwipePolicy.isMutatingDirection(pinLevel, eligible, deltaX)) {
            return false
        }
        hapticSent = true
        return true
    }

    fun reset() {
        hapticSent = false
    }
}
