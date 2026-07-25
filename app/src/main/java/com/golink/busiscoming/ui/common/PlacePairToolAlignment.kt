package com.golink.busiscoming.ui.common

internal object PlacePairToolAlignment {
    fun centeredTop(
        inputTop: Int,
        inputBottom: Int,
        toolHeight: Int
    ): Int = (inputTop + inputBottom - toolHeight) / 2

    fun swapTop(
        originCenter: Int,
        destinationCenter: Int,
        originCandidateOccupiedHeight: Int,
        toolHeight: Int
    ): Int {
        val collapsedDestinationCenter =
            destinationCenter - originCandidateOccupiedHeight
        return (originCenter + collapsedDestinationCenter) / 2 - toolHeight / 2
    }
}
