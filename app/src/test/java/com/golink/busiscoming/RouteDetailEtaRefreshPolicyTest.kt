package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteDetailEtaRefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailEtaRefreshPolicyTest {
    @Test
    fun foregroundRefreshesImmediatelyWhenMissingOrStale() {
        assertTrue(RouteDetailEtaRefreshPolicy.shouldRefreshOnForeground(nowMillis = 100_000L, lastSuccessMillis = null))
        assertTrue(RouteDetailEtaRefreshPolicy.shouldRefreshOnForeground(nowMillis = 100_000L, lastSuccessMillis = 40_000L))
        assertFalse(RouteDetailEtaRefreshPolicy.shouldRefreshOnForeground(nowMillis = 100_000L, lastSuccessMillis = 40_001L))
    }
}
