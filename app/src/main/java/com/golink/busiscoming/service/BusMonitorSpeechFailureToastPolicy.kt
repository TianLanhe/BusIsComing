package com.golink.busiscoming.service

import androidx.annotation.StringRes
import com.golink.busiscoming.R

class BusMonitorSpeechFailureToastPolicy {
    private val reportedReasons = mutableSetOf<BusMonitorSpeechFailureReason>()

    fun shouldShow(
        voiceEnabled: Boolean,
        reason: BusMonitorSpeechFailureReason
    ): Boolean = voiceEnabled && reportedReasons.add(reason)

    fun reset() {
        reportedReasons.clear()
    }
}

object BusMonitorSpeechFailureMessage {
    @StringRes
    fun resourceId(reason: BusMonitorSpeechFailureReason): Int = when (reason) {
        BusMonitorSpeechFailureReason.NO_ENGINE -> R.string.tts_failure_no_engine
        BusMonitorSpeechFailureReason.INITIALIZATION_FAILED -> R.string.tts_failure_initialization
        BusMonitorSpeechFailureReason.INITIALIZATION_TIMEOUT ->
            R.string.tts_failure_initialization_timeout
        BusMonitorSpeechFailureReason.LANGUAGE_MISSING_DATA -> R.string.tts_failure_missing_data
        BusMonitorSpeechFailureReason.LANGUAGE_NOT_SUPPORTED ->
            R.string.tts_failure_unsupported_locale
        BusMonitorSpeechFailureReason.NO_COMPATIBLE_VOICE ->
            R.string.tts_failure_no_compatible_voice
        BusMonitorSpeechFailureReason.AUDIO_FOCUS_DENIED -> R.string.tts_failure_audio_focus
        BusMonitorSpeechFailureReason.SPEAK_REJECTED -> R.string.tts_failure_speak_rejected
        BusMonitorSpeechFailureReason.PLAYBACK_ERROR -> R.string.tts_failure_playback
        BusMonitorSpeechFailureReason.PLAYBACK_TIMEOUT -> R.string.tts_failure_playback_timeout
        BusMonitorSpeechFailureReason.RELEASED -> R.string.tts_failure_released
    }
}
