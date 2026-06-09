package com.example.busiscoming.service

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import com.example.busiscoming.data.model.BusMonitorSpeechFormatter
import com.example.busiscoming.data.model.BusMonitorTtsLanguagePolicy

enum class BusMonitorSpeechResult {
    SPOKEN,
    QUEUED,
    UNAVAILABLE
}

class BusMonitorSpeechController(context: Context) {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false
    private var ready = false
    private var released = false
    private var pendingSpeech: String? = null

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            initialized = true
            ready = status == TextToSpeech.SUCCESS && configureLanguage()
            if (ready) {
                pendingSpeech?.let { speak(it) }
                pendingSpeech = null
            } else {
                pendingSpeech = null
            }
        }
    }

    fun speak(text: String): BusMonitorSpeechResult {
        if (released) return BusMonitorSpeechResult.UNAVAILABLE
        if (!initialized) {
            pendingSpeech = text
            return BusMonitorSpeechResult.QUEUED
        }
        if (!ready) return BusMonitorSpeechResult.UNAVAILABLE
        val engine = textToSpeech ?: return BusMonitorSpeechResult.UNAVAILABLE
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bus-monitor-${System.currentTimeMillis()}")
        }.getOrDefault(TextToSpeech.ERROR)
        return if (result == TextToSpeech.SUCCESS) {
            BusMonitorSpeechResult.SPOKEN
        } else {
            BusMonitorSpeechResult.UNAVAILABLE
        }
    }

    fun release() {
        released = true
        pendingSpeech = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
    }

    private fun configureLanguage(): Boolean {
        val engine = textToSpeech ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
        return BusMonitorTtsLanguagePolicy.chooseSupportedLocale { locale ->
            engine.setLanguage(locale)
        } != null
    }
}

class BusMonitorSpeechPreviewer(context: Context) {
    private val controller = BusMonitorSpeechController(context.applicationContext)

    fun playPreview(): BusMonitorSpeechResult {
        return controller.speak(BusMonitorSpeechFormatter.previewPhrase())
    }

    fun release() {
        controller.release()
    }
}
