package com.golink.busiscoming

import com.golink.busiscoming.data.model.CtbRouteSlice
import com.golink.busiscoming.data.repository.CtbRouteSliceLoader
import com.golink.busiscoming.data.repository.CtbRouteSliceSource
import com.golink.busiscoming.data.repository.CtbRouteSliceStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CtbRouteSliceLoaderTest {
    @Test
    fun loadsTwoDirectionsAndFetchesEachUniqueStopOnlyOnce() {
        val routeCalls = mutableListOf<String>()
        val stopCalls = mutableListOf<String>()
        val store = MemorySliceStore()
        val loader = CtbRouteSliceLoader(
            source = object : CtbRouteSliceSource {
                override fun fetchRouteStops(route: String, direction: String): String {
                    routeCalls += direction
                    return if (direction == "outbound") {
                        routeStops(route, "O", listOf("001227", "001228"))
                    } else {
                        routeStops(route, "I", listOf("001228", "001229"))
                    }
                }

                override fun fetchStop(stopId: String): String {
                    stopCalls += stopId
                    return stop(stopId)
                }
            },
            store = store
        )

        val slices = loader.loadRoute("118", "2026-08-17")

        assertEquals(listOf("outbound", "inbound"), routeCalls)
        assertEquals(setOf("001227", "001228", "001229"), stopCalls.toSet())
        assertEquals(3, stopCalls.size)
        assertEquals(setOf("outbound", "inbound"), slices.map { it.direction }.toSet())
        assertEquals(slices.toSet(), store.values.values.toSet())
    }

    @Test
    fun partialStopFailureDoesNotPublishEitherDirection() {
        val store = MemorySliceStore().apply {
            values["118|outbound"] = CtbRouteSlice(
                "118",
                "outbound",
                "2026-08-16",
                "old",
                emptyList()
            )
        }
        val calls = AtomicInteger()
        val loader = CtbRouteSliceLoader(
            source = object : CtbRouteSliceSource {
                override fun fetchRouteStops(route: String, direction: String): String =
                    routeStops(route, if (direction == "outbound") "O" else "I", listOf("001227"))

                override fun fetchStop(stopId: String): String {
                    calls.incrementAndGet()
                    throw IOException("failed")
                }
            },
            store = store
        )

        assertThrows(IOException::class.java) { loader.loadRoute("118", "2026-08-17") }
        assertEquals("old", store.values.getValue("118|outbound").fingerprint)
        assertEquals(1, store.values.size)
    }

    private fun routeStops(route: String, bound: String, stops: List<String>): String {
        val data = stops.mapIndexed { index, stopId ->
            """{"co":"CTB","route":"$route","dir":"$bound","seq":"${index + 1}","stop":"$stopId"}"""
        }.joinToString(",")
        return """{"data":[$data]}"""
    }

    private fun stop(id: String): String {
        val suffix = id.takeLast(1).toInt()
        return """{"data":{"co":"CTB","stop":"$id","name_tc":"站$id","lat":"22.26$suffix","long":"114.24$suffix"}}"""
    }

    private class MemorySliceStore : CtbRouteSliceStore {
        val values = linkedMapOf<String, CtbRouteSlice>()

        override fun loadCtbRouteSlice(route: String, direction: String): CtbRouteSlice? =
            values["$route|$direction"]

        override fun saveCtbRouteSlices(slices: List<CtbRouteSlice>) {
            slices.forEach { values["${it.route}|${it.direction}"] = it }
        }
    }
}

