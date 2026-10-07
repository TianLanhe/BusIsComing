package com.golink.busiscoming.ui.main

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** 在接受新列表後、首次繪製前，以舊 layout 的最新序號與偏移安排新 layout。 */
class RouteListPositionLayoutManager(context: Context) : LinearLayoutManager(context) {
    private var pendingPosition: RouteListPosition? = null
    private var laidOutPaddingTop = 0

    fun preservePosition() {
        val first = findFirstVisibleItemPosition()
        val child = findViewByPosition(first)
        pendingPosition = child?.let {
            val margins = it.layoutParams as RecyclerView.LayoutParams
            RouteListPosition(first, getDecoratedTop(it) - margins.topMargin - laidOutPaddingTop)
        }
        requestLayout()
    }

    fun cancelPreservation() {
        pendingPosition = null
    }

    override fun scrollToPositionWithOffset(position: Int, offset: Int) {
        cancelPreservation()
        super.scrollToPositionWithOffset(position, offset)
    }

    override fun scrollToPosition(position: Int) {
        cancelPreservation()
        super.scrollToPosition(position)
    }

    override fun supportsPredictiveItemAnimations(): Boolean =
        pendingPosition == null && super.supportsPredictiveItemAnimations()

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (pendingPosition == null || state.isPreLayout) {
            super.onLayoutChildren(recycler, state)
            if (!state.isPreLayout) laidOutPaddingTop = paddingTop
            return
        }
        val snapshot = pendingPosition?.within(state.itemCount)
        pendingPosition = null
        if (snapshot == null) {
            super.onLayoutChildren(recycler, state)
            if (!state.isPreLayout) laidOutPaddingTop = paddingTop
            return
        }
        // 先量出目標項新高度；兩次 layout 都在同一繪製前完成，沒有延後 post 拉回。
        super.scrollToPositionWithOffset(snapshot.index, 0)
        super.onLayoutChildren(recycler, state)
        laidOutPaddingTop = paddingTop
        val target = findViewByPosition(snapshot.index) ?: return
        val margins = target.layoutParams as RecyclerView.LayoutParams
        val height = getDecoratedMeasuredHeight(target) + margins.topMargin + margins.bottomMargin
        val offset = snapshot.offsetForHeight(height)
        if (offset != 0) {
            super.scrollToPositionWithOffset(snapshot.index, offset)
            super.onLayoutChildren(recycler, state)
        }
    }
}
