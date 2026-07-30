package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.Place

/**
 * 搜尋頁展示層的模式；查詢 generation 與 callback 驗證仍由 [RouteQueryCoordinator] 擁有。
 */
enum class SearchDisplayMode {
    EDITING,
    QUERYING,
    RESULTS,
    EDITING_RESULTS,
    DIRTY_EDITING
}

/** 目前查詢結果可用的保存狀態。 */
enum class SearchSaveState {
    UNAVAILABLE,
    AVAILABLE,
    SAVED
}

/** 成功查詢所對應的起終點快照。 */
data class SearchQuerySnapshot(
    val origin: Place,
    val destination: Place
)

/**
 * 成功查詢結果的擁有權；token 在同一 Fragment 實例內單調遞增，即使 Place 相同，
 * 新查詢亦不會被舊保存對話框誤認為同一結果。
 */
data class SuccessfulSearchContext(
    val token: Long,
    val queryId: Int,
    val snapshot: SearchQuerySnapshot
)

class SuccessfulSearchContextState {
    private var nextToken = 0L
    private var current: SuccessfulSearchContext? = null

    fun recordSuccess(queryId: Int, snapshot: SearchQuerySnapshot): SuccessfulSearchContext {
        val context = SuccessfulSearchContext(
            token = ++nextToken,
            queryId = queryId,
            snapshot = snapshot
        )
        current = context
        return context
    }

    fun retainForRefresh(snapshot: SearchQuerySnapshot): Boolean =
        current?.snapshot == snapshot

    fun isCurrent(context: SuccessfulSearchContext): Boolean = current == context

    fun currentFor(snapshot: SearchQuerySnapshot, resultCount: Int): SuccessfulSearchContext? =
        current?.takeIf { resultCount > 0 && it.snapshot == snapshot }

    fun invalidate() {
        current = null
    }
}

/** 「本次行程」上下文僅能由仍在記憶體中的有效非空結果顯示。 */
object SearchTripContextVisibility {
    fun isVisible(mode: SearchDisplayMode, resultCount: Int): Boolean =
        resultCount > 0 && mode == SearchDisplayMode.RESULTS

    fun shouldRestoreFoldedContext(
        savedMode: SearchDisplayMode,
        retainedResultCount: Int,
        hasValidSnapshot: Boolean
    ): Boolean =
        savedMode == SearchDisplayMode.RESULTS && retainedResultCount > 0 && hasValidSnapshot
}

/**
 * 搜尋頁的純展示狀態。
 *
 * 不持有 query id、進行中旗標或 callback generation；呼叫端必須先由
 * [RouteQueryCoordinator] 驗證回呼仍屬目前查詢，才把結果事件交給此 reducer。
 */
class SearchPresentationState {
    var mode: SearchDisplayMode = SearchDisplayMode.EDITING
        private set
    var querySnapshot: SearchQuerySnapshot? = null
        private set
    var saveState: SearchSaveState = SearchSaveState.UNAVAILABLE
        private set

    fun beginQuery(origin: Place, destination: Place) {
        mode = SearchDisplayMode.QUERYING
        querySnapshot = SearchQuerySnapshot(origin, destination)
        saveState = SearchSaveState.UNAVAILABLE
    }

    fun completeWithResults(): Boolean {
        if (mode != SearchDisplayMode.QUERYING || querySnapshot == null) return false
        mode = SearchDisplayMode.RESULTS
        saveState = SearchSaveState.AVAILABLE
        return true
    }

    fun completeEmpty(): Boolean = completeWithoutResults()

    fun failQuery(): Boolean = completeWithoutResults()

    fun cancelQuery(): Boolean = completeWithoutResults()

    fun completeRefreshEmpty(): Boolean {
        if (mode != SearchDisplayMode.RESULTS || querySnapshot == null) return false
        resetToEditing()
        return true
    }

    fun beginEditingResults(): Boolean {
        if (mode != SearchDisplayMode.RESULTS || querySnapshot == null) return false
        mode = SearchDisplayMode.EDITING_RESULTS
        saveState = SearchSaveState.UNAVAILABLE
        return true
    }

    fun onInputChanged(): Boolean {
        when (mode) {
            SearchDisplayMode.QUERYING -> resetToEditing()
            SearchDisplayMode.RESULTS -> {
                mode = SearchDisplayMode.DIRTY_EDITING
                querySnapshot = null
                saveState = SearchSaveState.UNAVAILABLE
            }

            SearchDisplayMode.EDITING,
            SearchDisplayMode.EDITING_RESULTS,
            SearchDisplayMode.DIRTY_EDITING -> return false
        }
        return true
    }

    fun markSaved(): Boolean {
        if (
            mode != SearchDisplayMode.RESULTS ||
            querySnapshot == null ||
            saveState != SearchSaveState.AVAILABLE
        ) return false
        saveState = SearchSaveState.SAVED
        return true
    }

    private fun completeWithoutResults(): Boolean {
        if (mode != SearchDisplayMode.QUERYING) return false
        resetToEditing()
        return true
    }

    private fun resetToEditing() {
        mode = SearchDisplayMode.EDITING
        querySnapshot = null
        saveState = SearchSaveState.UNAVAILABLE
    }
}
