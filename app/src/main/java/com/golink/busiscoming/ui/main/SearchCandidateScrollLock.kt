package com.golink.busiscoming.ui.main

/**
 * 聚合搜尋頁兩個地點候選列表的外層捲動鎖定狀態。
 *
 * 此狀態只用於讓搜尋頁在候選展開時關閉刷新與啟用返回鍵處理。AppBar 的
 * scroll flags 和目前 offset 不會被改寫，候選手勢由 RecyclerView 自身擁有。
 */
internal class SearchCandidateScrollLock {
    private var outerScrollLocked = false

    fun update(
        originCandidatesVisible: Boolean,
        destinationCandidatesVisible: Boolean
    ): State {
        outerScrollLocked = originCandidatesVisible || destinationCandidatesVisible
        return State(outerScrollLocked)
    }

    fun reset() {
        outerScrollLocked = false
    }

    fun isOuterScrollLocked(): Boolean = outerScrollLocked

    data class State(val outerScrollLocked: Boolean)
}
