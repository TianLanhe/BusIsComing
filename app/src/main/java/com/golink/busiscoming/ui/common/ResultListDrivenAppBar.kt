package com.golink.busiscoming.ui.common

import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

/**
 * 禁止直接拖動 AppBar；保留 RecyclerView nested scroll 對既有 scroll flags 的驅動。
 */
object ResultListDrivenAppBar {
    fun install(appBar: AppBarLayout) {
        val params = appBar.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val behavior = (params.behavior as? AppBarLayout.Behavior)
            ?: AppBarLayout.Behavior().also { params.behavior = it }
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean = false
        })
    }
}
