package com.golink.busiscoming.ui.main

/** 搜尋結果區共用狀態卡的顯示狀態。 */
enum class SearchQueryStatusCard {
    HIDDEN,
    LOADING,
    EMPTY,
    FAILURE
}

/** 由展示模式與查詢真相來源共同導出的搜尋 UI 狀態。 */
data class SearchQueryUiModel(
    val isQueryEnabled: Boolean,
    val isQuerying: Boolean,
    val isRefreshing: Boolean,
    val statusCard: SearchQueryStatusCard
)

object SearchQueryUiPolicy {
    fun resolve(
        queryState: RouteQueryState,
        hasSubmittedQuery: Boolean,
        hasValidPlaces: Boolean,
        displayMode: SearchDisplayMode = SearchDisplayMode.EDITING,
        refreshFeedbackVisible: Boolean = false,
        refreshFeedbackBlocksQueries: Boolean = false
    ): SearchQueryUiModel {
        val isQuerying = queryState.isQueryInProgress ||
            displayMode == SearchDisplayMode.QUERYING ||
            refreshFeedbackBlocksQueries
        val isRefreshing = queryState.isRefreshing && queryState.results.isNotEmpty()
        val statusCard = when {
            isRefreshing || refreshFeedbackVisible -> SearchQueryStatusCard.HIDDEN
            isQuerying -> SearchQueryStatusCard.LOADING
            queryState.errorMessage != null && queryState.results.isEmpty() ->
                SearchQueryStatusCard.FAILURE
            hasSubmittedQuery && queryState.results.isEmpty() -> SearchQueryStatusCard.EMPTY
            else -> SearchQueryStatusCard.HIDDEN
        }
        return SearchQueryUiModel(
            isQueryEnabled = hasValidPlaces && !isQuerying,
            isQuerying = isQuerying,
            isRefreshing = isRefreshing,
            statusCard = statusCard
        )
    }
}
