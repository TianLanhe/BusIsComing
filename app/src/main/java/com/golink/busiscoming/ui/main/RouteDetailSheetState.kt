package com.golink.busiscoming.ui.main

import kotlin.math.roundToInt

enum class RouteDetailSheetDetent {
    SUMMARY,
    HALF,
    FULL
}

data class RouteDetailSheetMetrics(
    val summaryHeight: Int,
    val halfExpandedRatio: Float
)

object RouteDetailSheetPolicy {
    fun metrics(parentHeight: Int, summaryContentHeight: Int): RouteDetailSheetMetrics {
        require(parentHeight > 0) { "Parent height must be positive" }
        val summaryTarget = (parentHeight * SUMMARY_TARGET_RATIO).roundToInt()
        val maxVisibleHeight = (parentHeight * MAX_VISIBLE_RATIO).roundToInt()
        val summaryHeight = maxOf(summaryTarget, summaryContentHeight).coerceAtMost(maxVisibleHeight)
        val halfHeight = maxOf((parentHeight * HALF_TARGET_RATIO).roundToInt(), summaryHeight)
            .coerceAtMost(maxVisibleHeight)
        return RouteDetailSheetMetrics(
            summaryHeight = summaryHeight,
            halfExpandedRatio = halfHeight.toFloat() / parentHeight.toFloat()
        )
    }

    fun onHandleClick(current: RouteDetailSheetDetent): RouteDetailSheetDetent {
        return if (current == RouteDetailSheetDetent.FULL) {
            RouteDetailSheetDetent.SUMMARY
        } else {
            RouteDetailSheetDetent.FULL
        }
    }

    fun onSummaryContentSwipeUp(): RouteDetailSheetDetent = RouteDetailSheetDetent.FULL

    fun onDownwardSettle(current: RouteDetailSheetDetent): RouteDetailSheetDetent {
        return when (current) {
            RouteDetailSheetDetent.FULL -> RouteDetailSheetDetent.HALF
            RouteDetailSheetDetent.HALF,
            RouteDetailSheetDetent.SUMMARY -> RouteDetailSheetDetent.SUMMARY
        }
    }

    private const val SUMMARY_TARGET_RATIO = 0.27f
    private const val HALF_TARGET_RATIO = 0.55f
    private const val MAX_VISIBLE_RATIO = 0.95f
}

internal sealed interface RouteDetailMapTransitionAction {
    data object Ignore : RouteDetailMapTransitionAction
    data class TranslateAttribution(val translationY: Int) : RouteDetailMapTransitionAction
    data class ApplyCandidatePadding(
        val bottomPadding: Int,
        val attributionTranslationY: Int
    ) : RouteDetailMapTransitionAction
    data class CommitStablePadding(val bottomPadding: Int) : RouteDetailMapTransitionAction
}

internal class RouteDetailMapTransitionPolicy(
    private val candidateThresholdPx: Int = 64
) {
    private var generation = 0L
    private var activeGeneration: Long? = null
    private var stableVisibleHeight = 0
    private var candidateApplied = false

    fun begin(stableVisibleHeight: Int): Long {
        generation += 1L
        activeGeneration = generation
        this.stableVisibleHeight = stableVisibleHeight
        candidateApplied = false
        return generation
    }

    fun slide(
        generation: Long,
        visibleHeight: Int,
        upwardCandidatePadding: Int
    ): RouteDetailMapTransitionAction {
        if (generation != activeGeneration) return RouteDetailMapTransitionAction.Ignore
        val translationY = stableVisibleHeight - visibleHeight
        val upwardDistance = visibleHeight - stableVisibleHeight
        if (!candidateApplied && upwardDistance >= candidateThresholdPx) {
            candidateApplied = true
            return RouteDetailMapTransitionAction.ApplyCandidatePadding(
                upwardCandidatePadding,
                translationY
            )
        }
        return RouteDetailMapTransitionAction.TranslateAttribution(translationY)
    }

    fun settle(
        generation: Long,
        stableVisibleHeight: Int
    ): RouteDetailMapTransitionAction {
        if (generation != activeGeneration) return RouteDetailMapTransitionAction.Ignore
        activeGeneration = null
        return RouteDetailMapTransitionAction.CommitStablePadding(stableVisibleHeight)
    }

    fun cancel(generation: Long) {
        if (generation == activeGeneration) activeGeneration = null
    }
}
