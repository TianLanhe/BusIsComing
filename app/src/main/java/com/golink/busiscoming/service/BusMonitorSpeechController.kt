package com.golink.busiscoming.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.golink.busiscoming.data.model.BusMonitorTtsLanguagePolicy
import com.golink.busiscoming.data.model.BusMonitorTtsLanguageSelection
import com.golink.busiscoming.data.model.BusMonitorTtsLanguageUnavailableReason
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.localization.TtsLanguageFamily
import java.util.Locale

sealed class BusMonitorSpeechResult {
    object Queued : BusMonitorSpeechResult()
    object Accepted : BusMonitorSpeechResult()
    object Started : BusMonitorSpeechResult()
    object Completed : BusMonitorSpeechResult()
    object Stopped : BusMonitorSpeechResult()
    data class Failure(
        val reason: BusMonitorSpeechFailureReason,
        val detail: String? = null
    ) : BusMonitorSpeechResult()

    val countsAsSpoken: Boolean
        get() = this == Started || this == Completed
}

enum class BusMonitorSpeechFailureReason {
    NO_ENGINE,
    INITIALIZATION_FAILED,
    INITIALIZATION_TIMEOUT,
    LANGUAGE_MISSING_DATA,
    LANGUAGE_NOT_SUPPORTED,
    NO_COMPATIBLE_VOICE,
    AUDIO_FOCUS_DENIED,
    SPEAK_REJECTED,
    PLAYBACK_ERROR,
    PLAYBACK_TIMEOUT,
    RELEASED
}

enum class BusMonitorSpeechAudioMode {
    PREVIEW,
    MONITOR
}

typealias BusMonitorSpeechEventListener = (BusMonitorSpeechResult) -> Unit

class BusMonitorSpeechController(
    context: Context,
    private val languageFamily: TtsLanguageFamily = AppLanguageRuntime.snapshot().ttsLanguageFamily,
    private val readinessListener: ((BusMonitorSpeechResult.Failure) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val audioManager: AudioManager? = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableMapOf<String, BusMonitorSpeechEventListener>()
    private val focusHandles = mutableMapOf<String, AudioFocusHandle>()
    private val startedUtterances = mutableSetOf<String>()
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "TTS audio focus change=$focusChange")
    }
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false
    private var ready = false
    private var released = false
    private var pendingSpeech: PendingSpeech? = null
    private var readinessFailure: BusMonitorSpeechResult.Failure? = null
    private var selectedLocale: Locale? = null

    init {
        if (!hasTextToSpeechEngine()) {
            initialized = true
            recordReadinessFailure(
                BusMonitorSpeechResult.Failure(BusMonitorSpeechFailureReason.NO_ENGINE)
            )
            Log.w(TAG, "No TextToSpeech engine service found")
        } else {
            textToSpeech = TextToSpeech(appContext) { status ->
                if (initialized && readinessFailure?.reason ==
                    BusMonitorSpeechFailureReason.INITIALIZATION_TIMEOUT
                ) {
                    return@TextToSpeech
                }
                initialized = true
                Log.d(TAG, "TextToSpeech init status=$status engine=${textToSpeech?.defaultEngine}")
                ready = if (status == TextToSpeech.SUCCESS) {
                    configureLanguage()
                } else {
                    recordReadinessFailure(
                        BusMonitorSpeechResult.Failure(
                            reason = BusMonitorSpeechFailureReason.INITIALIZATION_FAILED,
                            detail = "status=$status"
                        )
                    )
                    false
                }
                if (ready) {
                    pendingSpeech?.let { speak(it.text, it.mode, it.listener) }
                } else {
                    pendingSpeech?.listener?.invoke(readinessFailure ?: initializationFailure())
                }
                pendingSpeech = null
            }
            textToSpeech?.setOnUtteranceProgressListener(progressListener())
            scheduleInitializationTimeout()
        }
    }

    fun speak(
        text: String,
        mode: BusMonitorSpeechAudioMode = BusMonitorSpeechAudioMode.MONITOR,
        listener: BusMonitorSpeechEventListener? = null
    ): BusMonitorSpeechResult {
        if (released) return notifyResult(
            listener,
            BusMonitorSpeechResult.Failure(BusMonitorSpeechFailureReason.RELEASED)
        )
        if (!initialized) {
            pendingSpeech = PendingSpeech(text = text, mode = mode, listener = listener)
            return notifyResult(listener, BusMonitorSpeechResult.Queued)
        }
        if (!ready) {
            return notifyResult(listener, readinessFailure ?: initializationFailure())
        }
        val engine = textToSpeech ?: return notifyResult(
            listener,
            BusMonitorSpeechResult.Failure(BusMonitorSpeechFailureReason.NO_ENGINE)
        )
        applyAudioAttributes(engine, mode)
        val audioFocusHandle = requestAudioFocus(mode) ?: return notifyResult(
            listener,
            BusMonitorSpeechResult.Failure(
                reason = BusMonitorSpeechFailureReason.AUDIO_FOCUS_DENIED,
                detail = "mode=$mode"
            )
        )
        val utteranceId = "bus-monitor-${System.currentTimeMillis()}"
        focusHandles[utteranceId] = audioFocusHandle
        listeners[utteranceId] = { result ->
            if (result.isTerminal) {
                focusHandles.remove(utteranceId)?.release()
            }
            listener?.invoke(result)
        }
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        Log.d(TAG, "TextToSpeech speak result=$result utteranceId=$utteranceId mode=$mode locale=$selectedLocale")
        return if (result == TextToSpeech.SUCCESS) {
            val accepted = BusMonitorSpeechResult.Accepted
            listeners[utteranceId]?.invoke(accepted)
            scheduleStartTimeout(utteranceId)
            accepted
        } else {
            listeners.remove(utteranceId)
            focusHandles.remove(utteranceId)?.release()
            notifyResult(
                listener,
                BusMonitorSpeechResult.Failure(
                    reason = BusMonitorSpeechFailureReason.SPEAK_REJECTED,
                    detail = "result=$result"
                )
            )
        }
    }

    fun release() {
        released = true
        pendingSpeech = null
        listeners.clear()
        focusHandles.values.forEach { it.release() }
        focusHandles.clear()
        startedUtterances.clear()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
    }

    private fun configureLanguage(): Boolean {
        val engine = textToSpeech ?: return false
        val voices = engine.voices.orEmpty().toList()
        Log.d(
            TAG,
            "TTS configure engine=${engine.defaultEngine} requestedFamily=$languageFamily voiceCount=${voices.size}"
        )
        val selection = BusMonitorTtsLanguagePolicy.selectSupportedLocale(
            family = languageFamily,
            availableVoiceLocales = voices.map { it.locale }
        ) { locale ->
            val languageResult = engine.setLanguage(locale)
            val voice = voices.firstOrNull { it.locale.toLanguageTag().equals(locale.toLanguageTag(), true) }
            val voiceResult = voice?.let { engine.setVoice(it) } ?: TextToSpeech.LANG_NOT_SUPPORTED
            Log.d(
                TAG,
                "TTS candidate locale=${locale.toLanguageTag()} voice=${voice?.name} " +
                    "networkRequired=${voice?.isNetworkConnectionRequired} " +
                    "setLanguage=$languageResult setVoice=$voiceResult"
            )
            if (BusMonitorTtsLanguagePolicy.isLanguageUsable(languageResult) &&
                BusMonitorTtsLanguagePolicy.isLanguageUsable(voiceResult)
            ) {
                TextToSpeech.LANG_AVAILABLE
            } else if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                voiceResult == TextToSpeech.LANG_MISSING_DATA
            ) {
                TextToSpeech.LANG_MISSING_DATA
            } else {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        return when (selection) {
            is BusMonitorTtsLanguageSelection.Supported -> {
                selectedLocale = selection.locale
                Log.d(TAG, "TTS selected locale=${selection.locale.toLanguageTag()}")
                true
            }
            is BusMonitorTtsLanguageSelection.Unavailable -> {
                recordReadinessFailure(BusMonitorSpeechResult.Failure(
                    reason = when (selection.reason) {
                        BusMonitorTtsLanguageUnavailableReason.MISSING_DATA ->
                            BusMonitorSpeechFailureReason.LANGUAGE_MISSING_DATA
                        BusMonitorTtsLanguageUnavailableReason.NOT_SUPPORTED ->
                            BusMonitorSpeechFailureReason.LANGUAGE_NOT_SUPPORTED
                        BusMonitorTtsLanguageUnavailableReason.NO_COMPATIBLE_VOICE ->
                            BusMonitorSpeechFailureReason.NO_COMPATIBLE_VOICE
                    },
                    detail = selection.checks.joinToString { "${it.locale.toLanguageTag()}=${it.result}" }
                ))
                Log.w(TAG, "TTS language unavailable: ${readinessFailure?.detail}")
                false
            }
        }
    }

    private fun scheduleInitializationTimeout() {
        mainHandler.postDelayed(
            {
                if (!initialized && !released) {
                    initialized = true
                    ready = false
                    val failure = BusMonitorSpeechResult.Failure(
                        BusMonitorSpeechFailureReason.INITIALIZATION_TIMEOUT
                    )
                    recordReadinessFailure(failure)
                    pendingSpeech?.listener?.invoke(failure)
                    pendingSpeech = null
                    textToSpeech?.shutdown()
                    textToSpeech = null
                    Log.w(TAG, "TextToSpeech initialization timed out")
                }
            },
            INITIALIZATION_TIMEOUT_MILLIS
        )
    }

    private fun recordReadinessFailure(failure: BusMonitorSpeechResult.Failure) {
        readinessFailure = failure
        readinessListener?.invoke(failure)
    }

    private fun applyAudioAttributes(engine: TextToSpeech, mode: BusMonitorSpeechAudioMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val attributes = audioAttributesFor(mode)
        Log.d(TAG, "TTS audio usage=${attributes.usage} mode=$mode")
        engine.setAudioAttributes(attributes)
    }

    private fun audioAttributesFor(mode: BusMonitorSpeechAudioMode): AudioAttributes {
        val usage = when (mode) {
            BusMonitorSpeechAudioMode.PREVIEW -> AudioAttributes.USAGE_MEDIA
            BusMonitorSpeechAudioMode.MONITOR -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }
        return AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private fun requestAudioFocus(mode: BusMonitorSpeechAudioMode): AudioFocusHandle? {
        if (mode != BusMonitorSpeechAudioMode.MONITOR) {
            return AudioFocusHandle.NoOp
        }
        val manager = audioManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributesFor(mode))
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            val result = manager.requestAudioFocus(request)
            Log.d(TAG, "TTS audio focus request result=$result mode=$mode")
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                AudioFocusHandle.Modern(manager, request)
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val result = manager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            Log.d(TAG, "TTS legacy audio focus request result=$result mode=$mode")
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                AudioFocusHandle.Legacy(manager, focusChangeListener)
            } else {
                null
            }
        }
    }

    private fun progressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                Log.d(TAG, "TTS utterance started id=$id")
                startedUtterances += id
                dispatch(id, BusMonitorSpeechResult.Started, terminal = false)
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                Log.d(TAG, "TTS utterance completed id=$id")
                dispatch(id, BusMonitorSpeechResult.Completed, terminal = true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: return
                Log.w(TAG, "TTS utterance error id=$id")
                dispatch(
                    id,
                    BusMonitorSpeechResult.Failure(BusMonitorSpeechFailureReason.PLAYBACK_ERROR),
                    terminal = true
                )
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                val id = utteranceId ?: return
                Log.d(TAG, "TTS utterance stopped id=$id interrupted=$interrupted")
                dispatch(id, BusMonitorSpeechResult.Stopped, terminal = true)
            }
        }
    }

    private fun scheduleStartTimeout(utteranceId: String) {
        mainHandler.postDelayed(
            {
                if (listeners.containsKey(utteranceId) && utteranceId !in startedUtterances) {
                    Log.w(TAG, "TTS utterance did not start before timeout id=$utteranceId")
                    dispatch(
                        utteranceId,
                        BusMonitorSpeechResult.Failure(BusMonitorSpeechFailureReason.PLAYBACK_TIMEOUT),
                        terminal = true
                    )
                }
            },
            START_TIMEOUT_MILLIS
        )
    }

    private fun dispatch(
        utteranceId: String,
        result: BusMonitorSpeechResult,
        terminal: Boolean
    ) {
        val listener = listeners[utteranceId]
        if (terminal) {
            listeners.remove(utteranceId)
            startedUtterances -= utteranceId
        }
        listener?.invoke(result)
    }

    private fun notifyResult(
        listener: BusMonitorSpeechEventListener?,
        result: BusMonitorSpeechResult
    ): BusMonitorSpeechResult {
        listener?.invoke(result)
        return result
    }

    private val BusMonitorSpeechResult.isTerminal: Boolean
        get() = this == BusMonitorSpeechResult.Completed ||
            this == BusMonitorSpeechResult.Stopped ||
            this is BusMonitorSpeechResult.Failure

    private fun initializationFailure(): BusMonitorSpeechResult.Failure {
        return BusMonitorSpeechResult.Failure(BusMonitorSpeechFailureReason.INITIALIZATION_FAILED)
    }

    private fun hasTextToSpeechEngine(): Boolean {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            ).isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentServices(intent, 0).isNotEmpty()
        }
    }

    private data class PendingSpeech(
        val text: String,
        val mode: BusMonitorSpeechAudioMode,
        val listener: BusMonitorSpeechEventListener?
    )

    private sealed class AudioFocusHandle {
        object NoOp : AudioFocusHandle()

        data class Modern(
            val audioManager: AudioManager,
            val request: AudioFocusRequest
        ) : AudioFocusHandle()

        data class Legacy(
            val audioManager: AudioManager,
            val listener: AudioManager.OnAudioFocusChangeListener
        ) : AudioFocusHandle()

        fun release() {
            when (this) {
                NoOp -> Unit
                is Modern -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.abandonAudioFocusRequest(request)
                    }
                }
                is Legacy -> {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(listener)
                }
            }
        }
    }

    private companion object {
        const val TAG = "BusMonitorSpeech"
        const val START_TIMEOUT_MILLIS = 4_000L
        const val INITIALIZATION_TIMEOUT_MILLIS = 8_000L
    }
}
