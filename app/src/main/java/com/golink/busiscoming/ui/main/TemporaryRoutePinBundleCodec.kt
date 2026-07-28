package com.golink.busiscoming.ui.main

import android.os.Bundle
import com.golink.busiscoming.data.model.TemporaryRoutePinSavedState

object TemporaryRoutePinBundleCodec {
    private const val JOURNEY_IDS = "temporary_pin_journey_ids"
    private const val FINGERPRINTS = "temporary_pin_fingerprints"
    private const val TOKENS = "temporary_pin_tokens"

    fun write(bundle: Bundle, pins: List<TemporaryRoutePinSavedState>) {
        bundle.putLongArray(JOURNEY_IDS, pins.map { it.journeyId }.toLongArray())
        bundle.putStringArrayList(FINGERPRINTS, ArrayList(pins.map { it.fingerprint }))
        bundle.putLongArray(TOKENS, pins.map { it.pinnedAt }.toLongArray())
    }

    fun read(bundle: Bundle): List<TemporaryRoutePinSavedState> {
        val journeyIds = bundle.getLongArray(JOURNEY_IDS) ?: longArrayOf()
        val fingerprints = bundle.getStringArrayList(FINGERPRINTS) ?: arrayListOf()
        val tokens = bundle.getLongArray(TOKENS) ?: longArrayOf()
        return List(minOf(journeyIds.size, fingerprints.size, tokens.size)) { index ->
            TemporaryRoutePinSavedState(
                journeyId = journeyIds[index],
                fingerprint = fingerprints[index],
                pinnedAt = tokens[index]
            )
        }
    }
}
