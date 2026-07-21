package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.Place

object SearchResultSaveEligibility {
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
            !queryInProgress &&
            !queryFailed &&
            queryOrigin != null &&
            queryDestination != null &&
            queryOrigin == currentOrigin &&
            queryDestination == currentDestination
    }
}

class SearchCurrentPlaceRequestState {
    private var generation = 0
    private var autoAttempted = false
    private var silentSnapshotAttempted = false

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

    fun beginSilentSnapshotRequest(canRequest: Boolean): Int? {
        if (!canRequest || silentSnapshotAttempted) return null
        silentSnapshotAttempted = true
        return beginRequest()
    }

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
