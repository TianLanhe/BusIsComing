package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.PinLevel
import kotlin.math.abs
import kotlin.math.min

enum class RoutePinSwipeAction {
    PIN_TEMPORARY,
    PIN_PERSISTENT,
    CANCEL,
    REBOUND,
    UNAVAILABLE
}

object RoutePinSwipePolicy {
    const val hasFlingShortcut = false
    const val dismissThreshold = Float.MAX_VALUE

    fun action(
        pinLevel: PinLevel,
        eligible: Boolean,
        deltaX: Float,
        triggerDistance: Float,
        velocityX: Float = 0f
    ): RoutePinSwipeAction {
        @Suppress("UNUSED_VARIABLE")
        val ignoredVelocity = velocityX
        if (triggerDistance <= 0f || abs(deltaX) < triggerDistance) {
            return RoutePinSwipeAction.REBOUND
        }
        if (!eligible) return RoutePinSwipeAction.UNAVAILABLE
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

data class RoutePinSwipeGeometry(
    val triggerDistance: Float,
    val maxDistance: Float
) {
    fun clamp(deltaX: Float): Float {
        val magnitude = min(abs(deltaX), maxDistance)
        return if (deltaX < 0f) -magnitude else magnitude
    }

    companion object {
        fun fromLabel(
            labelWidth: Float,
            edgePadding: Float,
            overshoot: Float
        ): RoutePinSwipeGeometry {
            val triggerDistance = labelWidth.coerceAtLeast(0f) +
                edgePadding.coerceAtLeast(0f)
            return RoutePinSwipeGeometry(
                triggerDistance = triggerDistance,
                maxDistance = triggerDistance + overshoot.coerceAtLeast(0f)
            )
        }
    }
}

class RoutePinSwipeThresholdTracker {
    private var hapticSent = false

    fun shouldHaptic(
        pinLevel: PinLevel,
        eligible: Boolean,
        deltaX: Float,
        triggerDistance: Float
    ): Boolean {
        if (hapticSent || triggerDistance <= 0f) return false
        val crossed = abs(deltaX) >= triggerDistance
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

class RoutePinSwipeReleaseTracker {
    private var stableId: String? = null
    private var action: RoutePinSwipeAction? = null

    fun update(
        stableId: String,
        pinLevel: PinLevel,
        eligible: Boolean,
        deltaX: Float,
        triggerDistance: Float
    ) {
        this.stableId = stableId
        action = RoutePinSwipePolicy.action(
            pinLevel = pinLevel,
            eligible = eligible,
            deltaX = deltaX,
            triggerDistance = triggerDistance
        )
    }

    fun consume(stableId: String): RoutePinSwipeAction? {
        val result = action.takeIf { this.stableId == stableId }
        reset()
        return result
    }

    fun reset() {
        stableId = null
        action = null
    }
}
