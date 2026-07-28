package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.RoutePinSessionState
import com.golink.busiscoming.ui.main.SavedRoutePinLoadGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRoutePinLoadGateTest {
    @Test
    fun `first visible completion waits for routes and persistent pins in either order`() {
        val gate = SavedRoutePinLoadGate()
        gate.begin(queryId = 7, journeyId = 1L)

        assertNull(gate.acceptRoutes(7, listOf(route("118"))))
        val completion = gate.acceptPins(
            queryId = 7,
            journeyId = 1L,
            result = Result.success(listOf(RoutePinRecord("v1|118", PinLevel.PERSISTENT, 10L)))
        )

        assertEquals(listOf("118"), completion?.routes?.map { it.routeName })
        assertEquals(listOf("v1|118"), completion?.pins?.map { it.fingerprint })
        assertFalse(requireNotNull(completion).pinReadFailed)

        gate.begin(queryId = 8, journeyId = 1L)
        assertNull(gate.acceptPins(8, 1L, Result.success(emptyList())))
        assertEquals(
            listOf("8X"),
            gate.acceptRoutes(8, listOf(route("8X")))?.routes?.map { it.routeName }
        )
    }

    @Test
    fun `pin failure explicitly releases routes with empty persistent state`() {
        val gate = SavedRoutePinLoadGate()
        gate.begin(8, 1L)
        gate.acceptPins(8, 1L, Result.failure(IllegalStateException("db")))

        val completion = gate.acceptRoutes(8, listOf(route("8X")))

        assertTrue(requireNotNull(completion).pinReadFailed)
        assertTrue(completion.pins.isEmpty())
    }

    @Test
    fun `stale query or other journey cannot release current list`() {
        val gate = SavedRoutePinLoadGate()
        gate.begin(9, 2L)

        assertNull(gate.acceptRoutes(8, listOf(route("old"))))
        assertNull(gate.acceptPins(9, 1L, Result.success(emptyList())))
        assertNull(gate.acceptPins(8, 2L, Result.success(emptyList())))
    }

    @Test
    fun `empty successful result still completes without deleting dormant pins`() {
        val gate = SavedRoutePinLoadGate()
        gate.begin(10, 3L)
        gate.acceptPins(
            10,
            3L,
            Result.success(listOf(RoutePinRecord("v1|dormant", PinLevel.PERSISTENT, 4L)))
        )

        val completion = gate.acceptRoutes(10, emptyList())

        assertTrue(requireNotNull(completion).routes.isEmpty())
        assertEquals("v1|dormant", completion.pins.single().fingerprint)
    }

    @Test
    fun `query failure invalidation leaves session preferences untouched`() {
        val gate = SavedRoutePinLoadGate()
        val session = RoutePinSessionState()
        session.replacePersistent(
            4L,
            listOf(RoutePinRecord("v1|keep", PinLevel.PERSISTENT, 5L))
        )
        gate.begin(11, 4L)

        gate.invalidate()

        assertEquals("v1|keep", session.records(4L).single().fingerprint)
        assertNull(gate.acceptRoutes(11, listOf(route("late"))))
    }

    private fun route(name: String) = BusRouteOption(
        routeName = name,
        routeSegments = listOf(name),
        priceHkd = 10.0,
        durationMinutes = 30,
        arrivalMinutes = 5,
        transferCount = 0,
        walkingDistanceMeters = 100
    )
}
