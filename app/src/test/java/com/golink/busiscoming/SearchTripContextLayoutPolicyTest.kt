package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SearchTripContextLayoutPolicy
import com.golink.busiscoming.ui.main.SearchTripActionWidthPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTripContextLayoutPolicyTest {
    @Test
    fun `normal font keeps one row by ellipsizing the route at 360dp`() {
        assertTrue(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 1080,
                actionsWidthPx = 420,
                fontScale = 1f,
                gapPx = 24,
                minimumRouteWidthPx = 180
            )
        )
    }

    @Test
    fun `normal font reflows only when actions would consume the minimum route slot`() {
        assertFalse(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 600,
                actionsWidthPx = 420,
                fontScale = 1f,
                gapPx = 24,
                minimumRouteWidthPx = 180
            )
        )
    }

    @Test
    fun `large font always gives the route and actions their own rows`() {
        assertFalse(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 1600,
                actionsWidthPx = 300,
                fontScale = 1.3f,
                gapPx = 24,
                minimumRouteWidthPx = 180
            )
        )
        assertFalse(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 1600,
                actionsWidthPx = 300,
                fontScale = 2f,
                gapPx = 24,
                minimumRouteWidthPx = 180
            )
        )
    }

    @Test
    fun `preferred actions width is independent from weighted parent layout params`() {
        assertEquals(
            348,
            SearchTripActionWidthPolicy.totalWidth(
                visibleButtonWidthsPx = listOf(132, 196),
                horizontalMarginsPx = listOf(8, 12)
            )
        )
    }
}
