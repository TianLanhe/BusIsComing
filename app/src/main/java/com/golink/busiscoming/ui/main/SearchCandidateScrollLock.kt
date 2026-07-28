package com.golink.busiscoming.ui.main

/**
 * 聚合搜尋頁兩個地點候選列表的外層捲動鎖定狀態。
 *
 * 搜尋候選展開時，AppBar 必須保持在目前位置；最後一個候選關閉後才恢復
 * 展開前的 flags，而不是假設原本一定是 [AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL]。
 */
internal class SearchCandidateScrollLock {
    private var savedScrollFlags: Int? = null

    fun update(
        originCandidatesVisible: Boolean,
        destinationCandidatesVisible: Boolean,
        currentScrollFlags: Int
    ): State {
        val candidatesVisible = originCandidatesVisible || destinationCandidatesVisible
        if (candidatesVisible) {
            if (savedScrollFlags == null) {
                savedScrollFlags = currentScrollFlags
            }
            return State(
                outerScrollLocked = true,
                scrollFlagsToApply = if (currentScrollFlags == 0) null else 0
            )
        }

        val flagsToRestore = savedScrollFlags
        savedScrollFlags = null
        return State(
            outerScrollLocked = false,
            scrollFlagsToApply = flagsToRestore
        )
    }

    fun reset() {
        savedScrollFlags = null
    }

    data class State(
        val outerScrollLocked: Boolean,
        val scrollFlagsToApply: Int? = null
    )
}
