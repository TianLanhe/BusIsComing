package com.golink.busiscoming.ui.main

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

object RouteListViewportController {
    fun revealPinnedTopAfterAnimations(
        recyclerView: RecyclerView,
        animate: Boolean,
        isCurrent: () -> Boolean = { true }
    ) {
        recyclerView.post {
            if (!isCurrent()) return@post
            val animator = recyclerView.itemAnimator
            if (animator == null) {
                revealPinnedTop(recyclerView, animate)
                return@post
            }
            animator.isRunning {
                recyclerView.post {
                    if (isCurrent()) revealPinnedTop(recyclerView, animate)
                }
            }
        }
    }

    fun revealPinnedTop(recyclerView: RecyclerView, animate: Boolean) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        if (recyclerView.adapter?.itemCount == 0) return
        recyclerView.stopScroll()
        if (!animate) {
            layoutManager.scrollToPositionWithOffset(0, 0)
            return
        }
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START
        }
        smoothScroller.targetPosition = 0
        layoutManager.startSmoothScroll(smoothScroller)
    }
}
