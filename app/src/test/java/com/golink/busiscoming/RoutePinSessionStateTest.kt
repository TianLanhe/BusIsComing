package com.golink.busiscoming

import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.RoutePinSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePinSessionStateTest {
    @Test
    fun `temporary pins use strictly increasing tokens when time ties or rolls back`() {
        val state = RoutePinSessionState()

        val first = state.pinTemporary(1L, "a", nowMillis = 100L)
        val second = state.pinTemporary(1L, "b", nowMillis = 100L)
        val third = state.pinTemporary(1L, "c", nowMillis = 90L)

        assertEquals(listOf(102L, 101L, 100L), state.records(1L).map { it.pinnedAt })
        assertEquals(PinLevel.TEMPORARY, first.level)
        assertEquals(101L, second.pinnedAt)
        assertEquals(102L, third.pinnedAt)
    }

    @Test(expected = IllegalStateException::class)
    fun `token exhaustion refuses a duplicate maximum token`() {
        val state = RoutePinSessionState()
        state.replacePersistent(
            1L,
            listOf(RoutePinRecord("maximum", PinLevel.PERSISTENT, Long.MAX_VALUE))
        )

        state.pinTemporary(1L, "next", nowMillis = 1L)
    }

    @Test
    fun `promoting temporary pin preserves its token and position`() {
        val state = RoutePinSessionState()
        state.pinTemporary(1L, "first", 100L)
        state.pinTemporary(1L, "second", 101L)

        val promoted = state.promotePersistent(1L, "first")

        assertEquals(PinLevel.PERSISTENT, promoted?.level)
        assertEquals(100L, promoted?.pinnedAt)
        assertEquals(listOf("second", "first"), state.records(1L).map { it.fingerprint })
    }

    @Test
    fun `cancel and restore recover the complete previous state`() {
        val state = RoutePinSessionState()
        state.replacePersistent(
            1L,
            listOf(RoutePinRecord("route", PinLevel.PERSISTENT, 88L))
        )

        val snapshot = state.cancel(1L, "route")

        assertNull(state.record(1L, "route"))
        state.restore(requireNotNull(snapshot))
        assertEquals(
            RoutePinRecord("route", PinLevel.PERSISTENT, 88L),
            state.record(1L, "route")
        )
    }

    @Test
    fun `journeys are isolated and clearing temporary does not remove persistent`() {
        val state = RoutePinSessionState()
        state.pinTemporary(1L, "same", 10L)
        state.pinTemporary(2L, "same", 20L)
        state.replacePersistent(
            1L,
            listOf(RoutePinRecord("saved", PinLevel.PERSISTENT, 5L))
        )

        state.clearTemporary(1L)

        assertEquals(listOf("saved"), state.records(1L).map { it.fingerprint })
        assertEquals(listOf("same"), state.records(2L).map { it.fingerprint })
    }

    @Test
    fun `replacing persistent records preserves temporary and dormant records`() {
        val state = RoutePinSessionState()
        state.pinTemporary(1L, "temporary", 50L)

        state.replacePersistent(
            1L,
            listOf(
                RoutePinRecord("matched", PinLevel.PERSISTENT, 40L),
                RoutePinRecord("dormant", PinLevel.PERSISTENT, 30L)
            )
        )

        assertEquals(
            listOf("temporary", "matched", "dormant"),
            state.records(1L).map { it.fingerprint }
        )
    }

    @Test
    fun `loaded persistent record supersedes overlapping restored temporary state`() {
        val state = RoutePinSessionState()
        state.pinTemporary(1L, "same", 50L)

        state.replacePersistent(
            1L,
            listOf(RoutePinRecord("same", PinLevel.PERSISTENT, 40L))
        )

        assertEquals(
            RoutePinRecord("same", PinLevel.PERSISTENT, 40L),
            state.record(1L, "same")
        )
    }

    @Test
    fun `session has no artificial pin limit`() {
        val state = RoutePinSessionState()

        repeat(1_000) { index ->
            state.pinTemporary(1L, "route-$index", nowMillis = index.toLong())
        }

        assertEquals(1_000, state.records(1L).size)
    }

    @Test
    fun `mutation generation makes only the latest callback current`() {
        val state = RoutePinSessionState()

        val first = state.nextMutationGeneration(1L, "route")
        val second = state.nextMutationGeneration(1L, "route")

        assertFalse(state.isCurrentMutation(1L, "route", first))
        assertTrue(state.isCurrentMutation(1L, "route", second))
        assertFalse(state.isCurrentMutation(2L, "route", second))
    }

    @Test
    fun `persistent load preserves mutations started after that load began`() {
        val state = RoutePinSessionState()
        state.replacePersistent(
            1L,
            listOf(
                RoutePinRecord("cancelled", PinLevel.PERSISTENT, 30L),
                RoutePinRecord("removed-remotely", PinLevel.PERSISTENT, 20L)
            )
        )
        state.pinTemporary(1L, "promoted", 40L)
        val baseline = state.mutationGenerationSnapshot(1L)

        state.pinTemporary(1L, "new-temporary", 50L)
        state.nextMutationGeneration(1L, "new-temporary")
        state.cancel(1L, "cancelled")
        state.nextMutationGeneration(1L, "cancelled")
        state.promotePersistent(1L, "promoted")
        state.nextMutationGeneration(1L, "promoted")

        state.replacePersistentPreservingMutations(
            journeyId = 1L,
            records = listOf(
                RoutePinRecord("cancelled", PinLevel.PERSISTENT, 30L),
                RoutePinRecord("new-temporary", PinLevel.PERSISTENT, 50L),
                RoutePinRecord("loaded", PinLevel.PERSISTENT, 10L)
            ),
            baselineMutationGenerations = baseline
        )

        assertNull(state.record(1L, "cancelled"))
        assertEquals(PinLevel.TEMPORARY, state.record(1L, "new-temporary")?.level)
        assertEquals(PinLevel.PERSISTENT, state.record(1L, "promoted")?.level)
        assertNull(state.record(1L, "removed-remotely"))
        assertEquals(PinLevel.PERSISTENT, state.record(1L, "loaded")?.level)
    }

    @Test
    fun `saved state contains temporary records only and restores their tokens`() {
        val state = RoutePinSessionState()
        state.pinTemporary(1L, "temporary", 22L)
        state.replacePersistent(
            1L,
            listOf(RoutePinRecord("persistent", PinLevel.PERSISTENT, 11L))
        )

        val saved = state.temporarySavedState()
        val restored = RoutePinSessionState()
        restored.restoreTemporarySavedState(saved)

        assertEquals(
            listOf(RoutePinRecord("temporary", PinLevel.TEMPORARY, 22L)),
            restored.records(1L)
        )
    }
}
