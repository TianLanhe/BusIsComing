package com.golink.busiscoming

import com.golink.busiscoming.data.location.SystemLocationFixFilter
import org.junit.Assert.*
import org.junit.Test

class SystemLocationFixFilterTest {
    @Test fun staleAndFutureFixesDoNotCountAsFirstFix() {
        val filter = SystemLocationFixFilter(20_000)
        assertFalse(filter.accept(9_999, 30_000))
        assertFalse(filter.accept(30_001, 30_000))
        assertTrue(filter.accept(10_000, 30_000))
    }

    @Test fun lateLastKnownCannotReplaceNewLiveFix() {
        val filter = SystemLocationFixFilter(20_000)
        assertTrue(filter.accept(30_000, 30_000))
        assertFalse(filter.accept(29_000, 30_000))
        assertTrue(filter.accept(31_000, 31_001))
    }
}
