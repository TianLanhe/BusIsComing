package com.golink.busiscoming.data.model

import kotlin.math.max

enum class PinLevel {
    UNPINNED,
    TEMPORARY,
    PERSISTENT
}

data class RoutePinRecord(
    val fingerprint: String,
    val level: PinLevel,
    val pinnedAt: Long
) {
    init {
        require(fingerprint.isNotBlank())
        require(level != PinLevel.UNPINNED)
    }
}

data class RoutePinSnapshot(
    val journeyId: Long,
    val record: RoutePinRecord
)

data class TemporaryRoutePinSavedState(
    val journeyId: Long,
    val fingerprint: String,
    val pinnedAt: Long
)

class RoutePinSessionState {
    private val recordsByJourney = mutableMapOf<Long, MutableMap<String, RoutePinRecord>>()
    private val mutationGenerations = mutableMapOf<Pair<Long, String>, Long>()
    private var maxKnownPinnedAt: Long = Long.MIN_VALUE

    fun pinTemporary(journeyId: Long, fingerprint: String, nowMillis: Long): RoutePinRecord {
        require(fingerprint.isNotBlank())
        record(journeyId, fingerprint)?.let { return it }
        val record = RoutePinRecord(
            fingerprint = fingerprint,
            level = PinLevel.TEMPORARY,
            pinnedAt = nextPinnedAt(nowMillis)
        )
        recordsFor(journeyId)[fingerprint] = record
        return record
    }

    fun promotePersistent(journeyId: Long, fingerprint: String): RoutePinRecord? {
        val current = record(journeyId, fingerprint) ?: return null
        if (current.level == PinLevel.PERSISTENT) return current
        return current.copy(level = PinLevel.PERSISTENT).also { promoted ->
            recordsFor(journeyId)[fingerprint] = promoted
        }
    }

    fun cancel(journeyId: Long, fingerprint: String): RoutePinSnapshot? {
        val removed = recordsByJourney[journeyId]?.remove(fingerprint) ?: return null
        removeEmptyJourney(journeyId)
        return RoutePinSnapshot(journeyId, removed)
    }

    fun restore(snapshot: RoutePinSnapshot) {
        val record = snapshot.record
        recordsFor(snapshot.journeyId)[record.fingerprint] = record
        maxKnownPinnedAt = max(maxKnownPinnedAt, record.pinnedAt)
    }

    fun replacePersistent(journeyId: Long, records: List<RoutePinRecord>) {
        val journeyRecords = recordsFor(journeyId)
        journeyRecords.entries.removeAll { it.value.level == PinLevel.PERSISTENT }
        records.forEach { source ->
            val persistent = source.copy(level = PinLevel.PERSISTENT)
            journeyRecords[persistent.fingerprint] = persistent
            maxKnownPinnedAt = max(maxKnownPinnedAt, persistent.pinnedAt)
        }
        removeEmptyJourney(journeyId)
    }

    fun replacePersistentPreservingMutations(
        journeyId: Long,
        records: List<RoutePinRecord>,
        baselineMutationGenerations: Map<String, Long>
    ) {
        val changedFingerprints = buildSet {
            val fingerprints = recordsByJourney[journeyId].orEmpty().keys +
                records.map { it.fingerprint } +
                mutationGenerations.keys
                    .asSequence()
                    .filter { it.first == journeyId }
                    .map { it.second }
                    .toSet()
            fingerprints.forEach { fingerprint ->
                val baseline = baselineMutationGenerations[fingerprint] ?: 0L
                val current = mutationGenerations[journeyId to fingerprint] ?: 0L
                if (current != baseline) add(fingerprint)
            }
        }
        val journeyRecords = recordsFor(journeyId)
        journeyRecords.entries.removeAll { entry ->
            entry.value.level == PinLevel.PERSISTENT &&
                entry.key !in changedFingerprints
        }
        records.forEach { source ->
            if (source.fingerprint !in changedFingerprints) {
                val persistent = source.copy(level = PinLevel.PERSISTENT)
                journeyRecords[persistent.fingerprint] = persistent
                maxKnownPinnedAt = max(maxKnownPinnedAt, persistent.pinnedAt)
            }
        }
        removeEmptyJourney(journeyId)
    }

    fun record(journeyId: Long, fingerprint: String): RoutePinRecord? =
        recordsByJourney[journeyId]?.get(fingerprint)

    fun records(journeyId: Long): List<RoutePinRecord> =
        recordsByJourney[journeyId].orEmpty().values.sortedByDescending { it.pinnedAt }

    fun clearTemporary(journeyId: Long) {
        recordsByJourney[journeyId]?.entries?.removeAll {
            it.value.level == PinLevel.TEMPORARY
        }
        removeEmptyJourney(journeyId)
    }

    fun clearJourney(journeyId: Long) {
        recordsByJourney.remove(journeyId)
        mutationGenerations.keys.removeAll { it.first == journeyId }
    }

    fun nextMutationGeneration(journeyId: Long, fingerprint: String): Long {
        val key = journeyId to fingerprint
        val next = mutationGenerations.getOrDefault(key, 0L) + 1L
        mutationGenerations[key] = next
        return next
    }

    fun isCurrentMutation(journeyId: Long, fingerprint: String, generation: Long): Boolean =
        mutationGenerations[journeyId to fingerprint] == generation

    fun mutationGenerationSnapshot(journeyId: Long): Map<String, Long> {
        return mutationGenerations
            .filterKeys { it.first == journeyId }
            .mapKeys { it.key.second }
    }

    fun temporarySavedState(): List<TemporaryRoutePinSavedState> {
        return recordsByJourney.flatMap { (journeyId, records) ->
            records.values
                .filter { it.level == PinLevel.TEMPORARY }
                .map { TemporaryRoutePinSavedState(journeyId, it.fingerprint, it.pinnedAt) }
        }
    }

    fun restoreTemporarySavedState(saved: List<TemporaryRoutePinSavedState>) {
        saved.forEach { item ->
            val record = RoutePinRecord(item.fingerprint, PinLevel.TEMPORARY, item.pinnedAt)
            recordsFor(item.journeyId)[item.fingerprint] = record
            maxKnownPinnedAt = max(maxKnownPinnedAt, item.pinnedAt)
        }
    }

    private fun nextPinnedAt(nowMillis: Long): Long {
        check(maxKnownPinnedAt != Long.MAX_VALUE) { "Pinned route token space exhausted" }
        val nextAfterKnown = maxKnownPinnedAt + 1L
        return max(nowMillis, nextAfterKnown).also { maxKnownPinnedAt = it }
    }

    private fun recordsFor(journeyId: Long): MutableMap<String, RoutePinRecord> =
        recordsByJourney.getOrPut(journeyId) { linkedMapOf() }

    private fun removeEmptyJourney(journeyId: Long) {
        if (recordsByJourney[journeyId].isNullOrEmpty()) recordsByJourney.remove(journeyId)
    }
}
