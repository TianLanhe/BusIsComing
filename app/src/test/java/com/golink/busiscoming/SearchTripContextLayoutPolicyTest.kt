package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SearchTripContextLayoutPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTripContextLayoutPolicyTest {
    @Test
    fun `uses one row only when normal text has enough measured width`() {
        assertTrue(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 800,
                routeWidthPx = 420,
                actionsWidthPx = 300,
                fontScale = 1f,
                gapPx = 24
            )
        )
        assertFalse(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 700,
                routeWidthPx = 420,
                actionsWidthPx = 300,
                fontScale = 1f,
                gapPx = 24
            )
        )
    }

    @Test
    fun `large font always gives the route and actions their own rows`() {
        assertFalse(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 1600,
                routeWidthPx = 420,
                actionsWidthPx = 300,
                fontScale = 1.3f,
                gapPx = 24
            )
        )
        assertFalse(
            SearchTripContextLayoutPolicy.usesSingleRow(
                availableWidthPx = 1600,
                routeWidthPx = 420,
                actionsWidthPx = 300,
                fontScale = 2f,
                gapPx = 24
            )
        )
    }
}
