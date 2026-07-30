package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.Place

object SearchResultSaveEligibility {
    @Suppress("UNUSED_PARAMETER")
    fun isVisible(
        queryOrigin: Place?,
        queryDestination: Place?,
        currentOrigin: Place?,
        currentDestination: Place?,
        resultCount: Int,
        queryInProgress: Boolean,
        queryFailed: Boolean
    ): Boolean {
        return resultCount > 0 &&
            queryOrigin != null &&
            queryDestination != null &&
            queryOrigin == currentOrigin &&
            queryDestination == currentDestination
    }
}

/** 以成功查詢的 Place pair 為身份，判斷潛在輸入事件是否真的令結果失效。 */
object SearchPlacePairMutationPolicy {
    fun shouldInvalidate(
        querySnapshot: SearchQuerySnapshot?,
        currentOrigin: Place?,
        currentDestination: Place?
    ): Boolean =
        querySnapshot == null ||
            querySnapshot.origin != currentOrigin ||
            querySnapshot.destination != currentDestination
}

class SearchCurrentPlaceRequestState {
    private var generation = 0
    private var autoAttempted = false

    var isPending: Boolean = false
        private set

    fun beginAutoRequest(
        hasSelectedOrigin: Boolean,
        originInput: String,
        hasSubmittedQuery: Boolean
    ): Int? {
        if (autoAttempted) return null
        autoAttempted = true
        if (hasSelectedOrigin || originInput.isNotBlank() || hasSubmittedQuery) return null
        return beginRequest()
    }

    fun beginManualRequest(): Int = beginRequest()

    fun invalidate() {
        generation += 1
        isPending = false
    }

    fun isCurrent(token: Int): Boolean = token == generation

    fun finish(token: Int): Boolean {
        if (!isCurrent(token)) return false
        isPending = false
        return true
    }

    private fun beginRequest(): Int {
        generation += 1
        isPending = true
        return generation
    }
}

class SearchCandidateLocationSnapshotRequestState {
    private var generation = 0
    private var attempted = false

    fun beginRequest(canRequest: Boolean): Int? {
        if (!canRequest || attempted) return null
        attempted = true
        generation += 1
        return generation
    }

    fun finish(token: Int): Boolean = token == generation

    fun invalidatePending() {
        generation += 1
    }

    fun resetForNextGeneration() {
        generation += 1
        attempted = false
    }
}
