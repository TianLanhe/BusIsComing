package com.golink.busiscoming

import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.RoutePinSessionState
import com.golink.busiscoming.ui.main.RoutePinSessionReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutePinSessionReconcilerTest {
    @Test
    fun `rename preserves every pin while endpoint edit clears the journey session`() {
        val state = stateWithBothPins()
        val original = journey()

        RoutePinSessionReconciler.reconcile(
            listOf(original),
            listOf(original.copy(name = "新名稱")),
            state
        )
        assertEquals(2, state.records(original.id).size)

        RoutePinSessionReconciler.reconcile(
            listOf(original.copy(name = "新名稱")),
            listOf(original.copy(name = "新名稱", origin = Place("新起點", 23.0, 114.0))),
            state
        )
        assertEquals(emptyList<RoutePinRecord>(), state.records(original.id))
    }

    @Test
    fun `deleting journey clears its complete session without touching another journey`() {
        val state = stateWithBothPins()
        state.pinTemporary(2L, "other", 20L)

        RoutePinSessionReconciler.reconcile(listOf(journey()), emptyList(), state)

        assertNull(state.record(1L, "temporary"))
        assertNull(state.record(1L, "persistent"))
        assertEquals(PinLevel.TEMPORARY, state.record(2L, "other")?.level)
    }

    private fun stateWithBothPins() = RoutePinSessionState().apply {
        pinTemporary(1L, "temporary", 10L)
        replacePersistent(
            1L,
            listOf(RoutePinRecord("persistent", PinLevel.PERSISTENT, 5L))
        )
    }

    private fun journey() = RouteConfig(
        id = 1L,
        name = "上班",
        origin = Place("起點", 22.1, 114.1),
        destination = Place("終點", 22.2, 114.2)
    )
}
