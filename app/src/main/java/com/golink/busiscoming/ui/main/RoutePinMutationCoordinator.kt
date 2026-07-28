package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.model.RoutePinSessionState
import com.golink.busiscoming.data.model.RoutePinSnapshot

interface PinMutationStore {
    fun insertIfAbsent(
        journeyId: Long,
        record: RoutePinRecord,
        completion: (Boolean) -> Unit
    )

    fun delete(
        journeyId: Long,
        fingerprint: String,
        completion: (Boolean) -> Unit
    )
}

class RoutePinMutationCoordinator(
    private val sessionState: RoutePinSessionState,
    private val store: PinMutationStore,
    private val observer: Observer
) {
    fun promotePersistent(journeyId: Long, fingerprint: String) {
        val original = sessionState.record(journeyId, fingerprint) ?: return
        if (original.level == PinLevel.PERSISTENT) return
        val promoted = sessionState.promotePersistent(journeyId, fingerprint) ?: return
        val generation = sessionState.nextMutationGeneration(journeyId, fingerprint)
        observer.onPinStateChanged(journeyId)
        store.insertIfAbsent(journeyId, promoted) { success ->
            if (!sessionState.isCurrentMutation(journeyId, fingerprint, generation)) return@insertIfAbsent
            if (success) {
                observer.onPersistentSaved(journeyId, promoted)
            } else {
                sessionState.cancel(journeyId, fingerprint)
                sessionState.restore(RoutePinSnapshot(journeyId, original))
                observer.onPinStateChanged(journeyId)
                observer.onFailure(journeyId, Failure.SAVE)
            }
        }
    }

    fun cancel(journeyId: Long, fingerprint: String): RoutePinSnapshot? {
        val snapshot = sessionState.cancel(journeyId, fingerprint) ?: return null
        val generation = sessionState.nextMutationGeneration(journeyId, fingerprint)
        observer.onPinStateChanged(journeyId)
        if (snapshot.record.level != PinLevel.PERSISTENT) {
            observer.onCancelled(snapshot)
            return snapshot
        }
        store.delete(journeyId, fingerprint) { success ->
            if (!sessionState.isCurrentMutation(journeyId, fingerprint, generation)) return@delete
            if (success) {
                observer.onCancelled(snapshot)
            } else {
                sessionState.restore(snapshot)
                observer.onPinStateChanged(journeyId)
                observer.onFailure(journeyId, Failure.CANCEL)
            }
        }
        return snapshot
    }

    fun undo(snapshot: RoutePinSnapshot) {
        val journeyId = snapshot.journeyId
        val record = snapshot.record
        sessionState.restore(snapshot)
        val generation = sessionState.nextMutationGeneration(journeyId, record.fingerprint)
        observer.onPinStateChanged(journeyId)
        if (record.level != PinLevel.PERSISTENT) return
        store.insertIfAbsent(journeyId, record) { success ->
            if (!sessionState.isCurrentMutation(journeyId, record.fingerprint, generation)) {
                return@insertIfAbsent
            }
            if (!success) {
                sessionState.cancel(journeyId, record.fingerprint)
                observer.onPinStateChanged(journeyId)
                observer.onFailure(journeyId, Failure.UNDO)
            }
        }
    }

    enum class Failure {
        SAVE,
        CANCEL,
        UNDO
    }

    interface Observer {
        fun onPinStateChanged(journeyId: Long)
        fun onPersistentSaved(journeyId: Long, record: RoutePinRecord)
        fun onCancelled(snapshot: RoutePinSnapshot)
        fun onFailure(journeyId: Long, failure: Failure)
    }
}
