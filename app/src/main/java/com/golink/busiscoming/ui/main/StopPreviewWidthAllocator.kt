package com.golink.busiscoming.ui.main

import kotlin.math.roundToInt

data class StopPreviewWidthAllocation(
    val originWidth: Int,
    val destinationWidth: Int
)

object StopPreviewWidthAllocator {
    private const val MINIMUM_SHARE = 0.32f

    fun allocate(
        availableWidth: Int,
        originDesiredWidth: Int,
        destinationDesiredWidth: Int
    ): StopPreviewWidthAllocation {
        val available = availableWidth.coerceAtLeast(0)
        val originDesired = originDesiredWidth.coerceAtLeast(0)
        val destinationDesired = destinationDesiredWidth.coerceAtLeast(0)
        if (available == 0) return StopPreviewWidthAllocation(0, 0)
        if (originDesired + destinationDesired <= available) {
            return StopPreviewWidthAllocation(originDesired, destinationDesired)
        }

        val minimum = (available * MINIMUM_SHARE).roundToInt()
        val maximum = available - minimum
        if (originDesired <= minimum) {
            return StopPreviewWidthAllocation(
                originWidth = originDesired,
                destinationWidth = (available - originDesired).coerceAtMost(destinationDesired)
            )
        }
        if (destinationDesired <= minimum) {
            return StopPreviewWidthAllocation(
                originWidth = (available - destinationDesired).coerceAtMost(originDesired),
                destinationWidth = destinationDesired
            )
        }

        val desiredTotal = originDesired.toLong() + destinationDesired.toLong()
        val proportionalOrigin = (available * (originDesired.toDouble() / desiredTotal)).roundToInt()
        val originWidth = proportionalOrigin.coerceIn(minimum, maximum)
        return StopPreviewWidthAllocation(
            originWidth = originWidth,
            destinationWidth = available - originWidth
        )
    }
}
