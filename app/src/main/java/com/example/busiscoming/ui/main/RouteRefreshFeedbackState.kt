package com.example.busiscoming.ui.main

enum class RouteRefreshVisualState {
    IDLE,
    REFRESHING,
    SUCCESS
}

enum class RouteRefreshResult {
    NON_EMPTY,
    EMPTY
}

enum class RouteRefreshFinishAction {
    KEEP_RESULTS,
    SHOW_EMPTY_RESULTS
}

class RouteRefreshFeedbackState {
    var visualState: RouteRefreshVisualState = RouteRefreshVisualState.IDLE
        private set

    val blocksQueries: Boolean
        get() = visualState != RouteRefreshVisualState.IDLE

    private var activeGeneration: Int? = null
    private var refreshResult: RouteRefreshResult? = null

    fun start(generation: Int): Boolean {
        if (visualState != RouteRefreshVisualState.IDLE) return false
        activeGeneration = generation
        refreshResult = null
        visualState = RouteRefreshVisualState.REFRESHING
        return true
    }

    fun succeed(generation: Int, result: RouteRefreshResult): Boolean {
        if (activeGeneration != generation || visualState != RouteRefreshVisualState.REFRESHING) {
            return false
        }
        refreshResult = result
        visualState = RouteRefreshVisualState.SUCCESS
        return true
    }

    fun finishSuccess(generation: Int): RouteRefreshFinishAction? {
        if (activeGeneration != generation || visualState != RouteRefreshVisualState.SUCCESS) {
            return null
        }
        val action = when (refreshResult) {
            RouteRefreshResult.EMPTY -> RouteRefreshFinishAction.SHOW_EMPTY_RESULTS
            RouteRefreshResult.NON_EMPTY -> RouteRefreshFinishAction.KEEP_RESULTS
            null -> return null
        }
        reset()
        return action
    }

    fun fail(generation: Int): Boolean {
        if (activeGeneration != generation || visualState != RouteRefreshVisualState.REFRESHING) {
            return false
        }
        reset()
        return true
    }

    fun cancel() {
        reset()
    }

    private fun reset() {
        activeGeneration = null
        refreshResult = null
        visualState = RouteRefreshVisualState.IDLE
    }
}
