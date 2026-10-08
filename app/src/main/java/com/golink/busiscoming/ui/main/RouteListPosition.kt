package com.golink.busiscoming.ui.main

data class RouteListPosition(val index: Int, val offset: Int) {
    fun within(itemCount: Int): RouteListPosition? =
        if (itemCount <= 0 || index < 0) null else copy(index = index.coerceAtMost(itemCount - 1))

    fun offsetForHeight(height: Int): Int = offset.coerceAtLeast(1 - height.coerceAtLeast(1))
}
