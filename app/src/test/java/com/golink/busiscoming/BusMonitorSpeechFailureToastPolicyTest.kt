package com.golink.busiscoming

import com.golink.busiscoming.service.BusMonitorSpeechFailureMessage
import com.golink.busiscoming.service.BusMonitorSpeechFailureReason
import com.golink.busiscoming.service.BusMonitorSpeechFailureToastPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorSpeechFailureToastPolicyTest {
    @Test
    fun reportsEachReasonOncePerMonitorSessionAndResetsForTheNextSession() {
        val policy = BusMonitorSpeechFailureToastPolicy()
        val reason = BusMonitorSpeechFailureReason.NO_COMPATIBLE_VOICE

        assertFalse(policy.shouldShow(voiceEnabled = false, reason))
        assertTrue(policy.shouldShow(voiceEnabled = true, reason))
        assertFalse(policy.shouldShow(voiceEnabled = true, reason))

        policy.reset()

        assertTrue(policy.shouldShow(voiceEnabled = true, reason))
    }

    @Test
    fun everyStableFailureReasonHasAnExplicitLocalizedMessage() {
        BusMonitorSpeechFailureReason.entries.forEach { reason ->
            assertTrue(reason.name, BusMonitorSpeechFailureMessage.resourceId(reason) != 0)
        }
    }
}
