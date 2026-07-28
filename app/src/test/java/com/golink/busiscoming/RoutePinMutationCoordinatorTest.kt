package com.golink.busiscoming

import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.RoutePinSessionState
import com.golink.busiscoming.data.model.RoutePinSnapshot
import com.golink.busiscoming.ui.main.PinMutationStore
import com.golink.busiscoming.ui.main.RoutePinMutationCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutePinMutationCoordinatorTest {
    @Test
    fun `persistent upgrade success keeps token and reports saved`() {
        val fixture = Fixture()
        fixture.state.pinTemporary(JOURNEY, FINGERPRINT, 10L)

        fixture.coordinator.promotePersistent(JOURNEY, FINGERPRINT)
        fixture.store.completeNext(success = true)

        assertEquals(RoutePinRecord(FINGERPRINT, PinLevel.PERSISTENT, 10L), fixture.record())
        assertEquals(listOf("changed", "saved"), fixture.events)
    }

    @Test
    fun `persistent upgrade is optimistic and failure restores temporary snapshot`() {
        val fixture = Fixture()
        fixture.state.pinTemporary(JOURNEY, FINGERPRINT, 10L)

        fixture.coordinator.promotePersistent(JOURNEY, FINGERPRINT)
        assertEquals(PinLevel.PERSISTENT, fixture.record()?.level)

        fixture.store.completeNext(success = false)

        assertEquals(RoutePinRecord(FINGERPRINT, PinLevel.TEMPORARY, 10L), fixture.record())
        assertEquals(listOf("changed", "changed", "save_failed"), fixture.events)
    }

    @Test
    fun `persistent cancellation failure restores level token and position`() {
        val fixture = Fixture()
        fixture.state.replacePersistent(
            JOURNEY,
            listOf(RoutePinRecord(FINGERPRINT, PinLevel.PERSISTENT, 10L))
        )

        fixture.coordinator.cancel(JOURNEY, FINGERPRINT)
        assertNull(fixture.record())
        fixture.store.completeNext(success = false)

        assertEquals(RoutePinRecord(FINGERPRINT, PinLevel.PERSISTENT, 10L), fixture.record())
        assertEquals("cancel_failed", fixture.events.last())
    }

    @Test
    fun `temporary cancellation succeeds without persistent store work`() {
        val fixture = Fixture()
        fixture.state.pinTemporary(JOURNEY, FINGERPRINT, 10L)

        val snapshot = fixture.coordinator.cancel(JOURNEY, FINGERPRINT)

        assertEquals(PinLevel.TEMPORARY, snapshot?.record?.level)
        assertNull(fixture.record())
        assertEquals(listOf("changed", "cancelled"), fixture.events)
        assertEquals(0, fixture.store.pendingCount)
    }

    @Test
    fun `undo before delete completion queues restore and stale delete callback cannot remove it`() {
        val fixture = Fixture()
        fixture.state.replacePersistent(
            JOURNEY,
            listOf(RoutePinRecord(FINGERPRINT, PinLevel.PERSISTENT, 10L))
        )

        val snapshot = requireNotNull(fixture.coordinator.cancel(JOURNEY, FINGERPRINT))
        fixture.coordinator.undo(snapshot)

        fixture.store.completeNext(success = true)
        assertEquals(PinLevel.PERSISTENT, fixture.record()?.level)
        fixture.store.completeNext(success = true)

        assertEquals(RoutePinRecord(FINGERPRINT, PinLevel.PERSISTENT, 10L), fixture.record())
    }

    @Test
    fun `rapid opposite action lets latest generation own visible state`() {
        val fixture = Fixture()
        fixture.state.pinTemporary(JOURNEY, FINGERPRINT, 10L)

        fixture.coordinator.promotePersistent(JOURNEY, FINGERPRINT)
        fixture.coordinator.cancel(JOURNEY, FINGERPRINT)
        fixture.store.completeNext(success = false)
        fixture.store.completeNext(success = true)

        assertNull(fixture.record())
        assertEquals("cancelled", fixture.events.last())
    }

    private class Fixture {
        val state = RoutePinSessionState()
        val store = FakeStore()
        val events = mutableListOf<String>()
        val coordinator = RoutePinMutationCoordinator(
            sessionState = state,
            store = store,
            observer = object : RoutePinMutationCoordinator.Observer {
                override fun onPinStateChanged(journeyId: Long) {
                    events += "changed"
                }

                override fun onPersistentSaved(journeyId: Long, record: RoutePinRecord) {
                    events += "saved"
                }

                override fun onCancelled(snapshot: RoutePinSnapshot) {
                    events += "cancelled"
                }

                override fun onFailure(
                    journeyId: Long,
                    failure: RoutePinMutationCoordinator.Failure
                ) {
                    events += when (failure) {
                        RoutePinMutationCoordinator.Failure.SAVE -> "save_failed"
                        RoutePinMutationCoordinator.Failure.CANCEL -> "cancel_failed"
                        RoutePinMutationCoordinator.Failure.UNDO -> "undo_failed"
                    }
                }
            }
        )

        fun record(): RoutePinRecord? = state.record(JOURNEY, FINGERPRINT)
    }

    private class FakeStore : PinMutationStore {
        private val completions = ArrayDeque<(Boolean) -> Unit>()
        val pendingCount: Int
            get() = completions.size

        override fun insertIfAbsent(
            journeyId: Long,
            record: RoutePinRecord,
            completion: (Boolean) -> Unit
        ) {
            completions += completion
        }

        override fun delete(
            journeyId: Long,
            fingerprint: String,
            completion: (Boolean) -> Unit
        ) {
            completions += completion
        }

        fun completeNext(success: Boolean) {
            completions.removeFirst().invoke(success)
        }
    }

    private companion object {
        const val JOURNEY = 1L
        const val FINGERPRINT = "v1|route"
    }
}
