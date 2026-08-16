package com.golink.busiscoming

import com.golink.busiscoming.data.model.GlobalStaticSource
import com.golink.busiscoming.data.repository.GlobalFetchResponse
import com.golink.busiscoming.data.repository.GlobalStaticDataFetcher
import com.golink.busiscoming.data.repository.KmbPacedGlobalStaticDataFetcher
import org.junit.Assert.assertEquals
import org.junit.Test

class KmbGlobalStaticRequestPacerTest {
    @Test
    fun spacesOnlyConsecutiveKmbGlobalRequestsWithoutRetryingThem() {
        var now = 10_000L
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val delegate = GlobalStaticDataFetcher { _, _ ->
            calls += 1
            GlobalFetchResponse(200, byteArrayOf(1), null, null)
        }
        val fetcher = KmbPacedGlobalStaticDataFetcher(
            delegate = delegate,
            minimumIntervalMillis = 1_500L,
            monotonicClock = { now },
            sleeper = { delay -> sleeps += delay; now += delay }
        )

        fetcher.fetch(GlobalStaticSource.KMB_ROUTES, null)
        now += 500L
        fetcher.fetch(GlobalStaticSource.KMB_ROUTE_STOPS, null)
        fetcher.fetch(GlobalStaticSource.CTB_ROUTES, null)

        assertEquals(listOf(1_000L), sleeps)
        assertEquals(3, calls)
    }
}
