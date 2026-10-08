package com.golink.busiscoming.ui.main

import androidx.recyclerview.widget.RecyclerView

/** AsyncListDiffer 負責提交世代；此 owner 世代另外阻止離頁後的恢復。 */
class RouteListSubmissionController(
    private val list: RecyclerView,
    private val adapter: BusRouteAdapter
) {
    private var generation = 0L
    private var navigationGeneration = 0L
    private var userScrollGeneration = 0L
    private var resetOnNextContent = false

    init {
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) userScrollGeneration++
            }
        })
    }

    fun navigationGuard(): () -> Boolean {
        val expected = navigationGeneration
        val expectedScroll = userScrollGeneration
        return { expected == navigationGeneration && expectedScroll == userScrollGeneration && list.isAttachedToWindow }
    }

    fun submit(items: List<BusRouteListItem>) {
        val expected = ++generation
        val hasCommittedPosition = adapter.itemCount > 0
        if (items.isEmpty()) {
            navigationGeneration++
            resetOnNextContent = true
            layout()?.cancelPreservation()
            // GONE 的列表未必會 layout 空結果；立即清除上一查詢留在 manager 的錨點。
            layout()?.scrollToPositionWithOffset(0, 0)
        }
        adapter.submitList(items) {
            if (expected == generation) {
                if (resetOnNextContent && items.isNotEmpty()) {
                    layout()?.scrollToPositionWithOffset(0, 0)
                    resetOnNextContent = false
                } else if (hasCommittedPosition && items.isNotEmpty() && list.isAttachedToWindow) {
                    layout()?.preservePosition()
                }
            }
        }
    }

    fun cancel(discardPendingList: Boolean = true) {
        generation++
        navigationGeneration++
        layout()?.cancelPreservation()
        // 使尚在計算的 diff 失效，否則它仍可能靠原卡片身份改變已導航的視窗。
        // 暫時離開前台仍須接受最新資料；只有新查詢／導航／View 銷毀才丟棄 diff。
        if (discardPendingList) adapter.submitList(adapter.currentList)
    }

    private fun layout() = list.layoutManager as? RouteListPositionLayoutManager
}
